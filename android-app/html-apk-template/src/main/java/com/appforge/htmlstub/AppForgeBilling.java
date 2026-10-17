package com.appforge.htmlstub;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import com.android.billingclient.api.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Real Play Billing adapter; offline builds never call this runtime. No purchase without a fresh gated foreground action. */
final class AppForgeBilling implements AutoCloseable {
    private final Activity activity;
    private final AppForgeBillingConfig config;
    private final BooleanSupplier foreground;
    private final Consumer<JSONObject> events;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService verifier=Executors.newSingleThreadExecutor();
    private final Map<String,ProductDetails> products=new HashMap<>();
    private final Set<String> checking=new HashSet<>(),completed=new HashSet<>();
    private BillingClient client;
    private volatile boolean online,closed;
    private boolean connecting,flow;
    private volatile int generation;
    AppForgeBilling(Activity activity,JSONObject options,BooleanSupplier foreground,Consumer<JSONObject> events) {
        this.activity=activity;this.foreground=foreground;this.events=events;config=new AppForgeBillingConfig(options);
    }
    private boolean active(int current){return !closed && online && current==generation && client!=null;}
    private JSONObject data(){return new JSONObject();}
    private void event(String state,JSONObject data) {
        try{data.put("state",state);events.accept(data);}catch(Exception failure){/* A retired document cannot receive billing data. */}
    }
    private void error(String state,int response){try{event(state,data().put("responseCode",response));}catch(Exception ignored){}}
    void network(boolean available) {
        if(closed)return;
        if(!available) {
            online=false;generation++;connecting=false;flow=false;products.clear();checking.clear();
            if(client!=null){client.endConnection();client=null;}
            event("OFFLINE",data());return;
        }
        online=true;
        if(client!=null && client.isReady()){refresh();return;}
        if(connecting)return;
        if(client!=null)client.endConnection();
        final int current=++generation;connecting=true;
        try {
        client=BillingClient.newBuilder(activity.getApplicationContext())
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())
            .setListener((result,purchases)->main.post(()->{
                if(!active(current))return;flow=false;
                if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK){error(result.getResponseCode()==BillingClient.BillingResponseCode.USER_CANCELED?"CANCELED":"SDK_ERROR",result.getResponseCode());return;}
                purchases(current,purchases);
            })).build();
        event("CONNECTING",data());
        client.startConnection(new BillingClientStateListener(){
            @Override public void onBillingSetupFinished(BillingResult result){main.post(()->{
                if(!active(current))return;connecting=false;
                if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK || !client.isReady()){error("UNAVAILABLE",result.getResponseCode());return;}
                event("READY",data());try{query(current,BillingClient.ProductType.INAPP,config.products);query(current,BillingClient.ProductType.SUBS,config.subscriptions);refresh();}catch(RuntimeException failure){error("SDK_QUERY_FAILED",-1);}
            });}
            @Override public void onBillingServiceDisconnected(){main.post(()->{if(active(current)){connecting=false;event("DISCONNECTED",data());}});}
        });
        } catch(RuntimeException failure) {connecting=false;generation++;if(client!=null){try{client.endConnection();}catch(RuntimeException ignored){}client=null;}error("SDK_INIT_FAILED",-1);}
    }
    private void requireReady() {
        if(closed || !online)throw new IllegalStateException("Billing offline; reconnect before retrying");
        if(client==null || !client.isReady())throw new IllegalStateException("Play Billing not ready");
    }
    String query() {
        requireReady();final int current=generation;
        query(current,BillingClient.ProductType.INAPP,config.products);query(current,BillingClient.ProductType.SUBS,config.subscriptions);return "REQUESTED";
    }
    private void query(int current,String type,Set<String> ids) {
        if(ids.isEmpty())return;
        List<QueryProductDetailsParams.Product> requested=new ArrayList<>();
        for(String id:ids)requested.add(QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build());
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(requested).build(),(result,details)->main.post(()->{
            if(!active(current))return;
            if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK || details==null){error("PRODUCT_QUERY_FAILED",result.getResponseCode());return;}
            try {
                for(String id:ids)products.remove(id);
                JSONArray items=new JSONArray();
                for(ProductDetails product:details.getProductDetailsList()) {
                    if(!ids.contains(product.getProductId()) || !type.equals(product.getProductType()))continue;
                    products.put(product.getProductId(),product);
                    JSONObject item=data().put("id",product.getProductId()).put("type",type).put("title",product.getTitle());
                    JSONArray offers=new JSONArray();
                    if(BillingClient.ProductType.INAPP.equals(type)) {
                        List<ProductDetails.OneTimePurchaseOfferDetails> values=product.getOneTimePurchaseOfferDetailsList();
                        if(values!=null)for(ProductDetails.OneTimePurchaseOfferDetails offer:values)offers.put(data().put("token",offer.getOfferToken()).put("price",offer.getFormattedPrice())
                            .put("currency",offer.getPriceCurrencyCode()).put("priceMicros",offer.getPriceAmountMicros()));
                    } else if(product.getSubscriptionOfferDetails()!=null)for(ProductDetails.SubscriptionOfferDetails offer:product.getSubscriptionOfferDetails()) {
                        JSONArray phases=new JSONArray();
                        for(ProductDetails.PricingPhase phase:offer.getPricingPhases().getPricingPhaseList())phases.put(data().put("price",phase.getFormattedPrice())
                            .put("currency",phase.getPriceCurrencyCode()).put("priceMicros",phase.getPriceAmountMicros()).put("period",phase.getBillingPeriod())
                            .put("cycles",phase.getBillingCycleCount()).put("recurrenceMode",phase.getRecurrenceMode()));
                        offers.put(data().put("token",offer.getOfferToken()).put("basePlanId",offer.getBasePlanId()).put("offerId",offer.getOfferId()).put("phases",phases));
                    }
                    items.put(item.put("offers",offers));
                }
                event("PRODUCTS",data().put("type",type).put("products",items).put("unfetched",details.getUnfetchedProductList().size()));
            }catch(Exception invalid){error("PRODUCT_DATA_INVALID",-1);}
        }));
    }
    String purchase(String id,String offerToken) {
        requireReady();
        if(!foreground.getAsBoolean())throw new SecurityException("Foreground purchase action required");
        if(flow || !checking.isEmpty())throw new IllegalStateException("Billing action already pending");
        ProductDetails product=products.get(id);
        if(product==null)throw new IllegalArgumentException("Query a configured product first");
        Set<String> offers=new HashSet<>();
        if(BillingClient.ProductType.INAPP.equals(product.getProductType())) {
            if(product.getOneTimePurchaseOfferDetailsList()!=null)for(ProductDetails.OneTimePurchaseOfferDetails offer:product.getOneTimePurchaseOfferDetailsList())offers.add(offer.getOfferToken());
        }else if(product.getSubscriptionOfferDetails()!=null)for(ProductDetails.SubscriptionOfferDetails offer:product.getSubscriptionOfferDetails())offers.add(offer.getOfferToken());
        if(offerToken==null || offerToken.length()>8192 || !offers.contains(offerToken))throw new IllegalArgumentException("Choose an offer returned by Play");
        BillingFlowParams.ProductDetailsParams selected=BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(offerToken).build();
        BillingResult result=client.launchBillingFlow(activity,BillingFlowParams.newBuilder().setProductDetailsParamsList(Collections.singletonList(selected)).build());
        if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK){error("FLOW_FAILED",result.getResponseCode());return "FLOW_FAILED";}
        flow=true;return "FLOW_OPENED"; // This is not a purchase receipt or entitlement.
    }
    String refresh() {
        requireReady();final int current=generation;
        for(String type:Arrays.asList(BillingClient.ProductType.INAPP,BillingClient.ProductType.SUBS))
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build(),(result,purchases)->main.post(()->{
                if(!active(current))return;
                if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK){error("PURCHASE_QUERY_FAILED",result.getResponseCode());return;}
                purchases(current,purchases);
            }));
        return "REQUESTED";
    }
    private void purchases(int current,List<Purchase> values) {
        if(values==null)return;
        for(Purchase purchase:values) {
            if(!activity.getPackageName().equals(purchase.getPackageName()) || purchase.getProducts().size()!=1 || purchase.getQuantity()<1 || purchase.getQuantity()>100){error("RECEIPT_UNSUPPORTED",-1);continue;}
            String product=purchase.getProducts().get(0);
            if(!config.products.contains(product) && !config.subscriptions.contains(product)){error("RECEIPT_UNCONFIGURED",-1);continue;}
            try {
                String hash=AppForgePurchaseVerifier.hash(purchase.getPurchaseToken());
                if(purchase.isSuspended()){event("SUSPENDED",data().put("productId",product).put("verified",false));continue;}
                if(purchase.getPurchaseState()==Purchase.PurchaseState.PENDING){event("PENDING",data().put("productId",product).put("verified",false));continue;}
                if(purchase.getPurchaseState()!=Purchase.PurchaseState.PURCHASED || completed.contains(hash) || !checking.add(hash))continue;
                event("VERIFYING",data().put("productId",product).put("verified",false).put("receiptSha256",hash));
                verifier.execute(()->{
                    if(closed || !online || current!=generation)return;
                    boolean verified=false;
                    try{verified=AppForgePurchaseVerifier.verify(config.endpoint,activity.getPackageName(),product,purchase.getPurchaseToken(),purchase.getQuantity());}catch(Exception rejected){}
                    final boolean accepted=verified;
                    main.post(()->{
                        if(!active(current)){checking.remove(hash);return;}
                        if(!accepted){checking.remove(hash);receipt("VERIFICATION_FAILED",product,hash,false,-1);return;}
                        finish(current,purchase,product,hash);
                    });
                });
            }catch(Exception invalid){error("RECEIPT_INVALID",-1);}
        }
    }
    private void finish(int current,Purchase purchase,String product,String hash) {
        if(!active(current) || !client.isReady()){checking.remove(hash);return;}
        if(config.consumables.contains(product)) {
            client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build(),(result,token)->main.post(()->{
                if(!active(current))return;checking.remove(hash);
                if(result.getResponseCode()==BillingClient.BillingResponseCode.OK && purchase.getPurchaseToken().equals(token)){completed.add(hash);receipt("COMPLETE",product,hash,true,0);}
                else receipt("CONSUME_FAILED",product,hash,false,result.getResponseCode()==0?-1:result.getResponseCode());
            }));
        }else if(purchase.isAcknowledged()) {
            checking.remove(hash);completed.add(hash);receipt("COMPLETE",product,hash,true,0);
        }else client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build(),result->main.post(()->{
            if(!active(current))return;checking.remove(hash);
            if(result.getResponseCode()==BillingClient.BillingResponseCode.OK){completed.add(hash);receipt("COMPLETE",product,hash,true,0);}
            else receipt("ACKNOWLEDGE_FAILED",product,hash,false,result.getResponseCode());
        }));
    }
    private void receipt(String state,String product,String hash,boolean verified,int response) {
        try{event(state,data().put("productId",product).put("receiptSha256",hash).put("verified",verified).put("responseCode",response));}catch(Exception ignored){}
    }
    @Override public void close() {
        closed=true;online=false;generation++;products.clear();checking.clear();completed.clear();verifier.shutdownNow();main.removeCallbacksAndMessages(null);
        if(client!=null){client.endConnection();client=null;}
    }
}
