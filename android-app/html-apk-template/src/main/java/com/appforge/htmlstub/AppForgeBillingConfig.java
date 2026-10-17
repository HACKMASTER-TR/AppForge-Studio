package com.appforge.htmlstub;

import org.json.JSONObject;
import java.net.URI;
import java.util.*;

/** Public app/product configuration only; no service-account keys or Play credentials in assets. */
final class AppForgeBillingConfig {
    final Set<String> products,subscriptions,consumables;
    final String endpoint;
    AppForgeBillingConfig(JSONObject config) {
        products=ids(config.optString("billingProductIds"));subscriptions=ids(config.optString("billingSubscriptionIds"));
        consumables=ids(config.optString("consumableProductIds"));endpoint=config.optString("purchaseVerificationUrl");
        if(products.size()+subscriptions.size()<1 || products.size()+subscriptions.size()>64 ||
            !Collections.disjoint(products,subscriptions) || !products.containsAll(consumables))throw new IllegalArgumentException("Invalid Billing product configuration");
        String removeAds=config.optString("removeAdsProductId");
        if(!removeAds.isEmpty() && (!products.contains(removeAds) || consumables.contains(removeAds)))throw new IllegalArgumentException("Invalid remove-ads product");
        endpoint(endpoint);
    }
    static Set<String> ids(String value) {
        if(value==null || value.length()>8192)throw new IllegalArgumentException("Product IDs exceed budget");
        Set<String> ids=new LinkedHashSet<>();
        for(String id:value.trim().split("[,;\\s]+"))if(!id.isEmpty()) {
            if(!id.matches("[a-z0-9][a-z0-9._-]{0,127}") || !ids.add(id) || ids.size()>64)throw new IllegalArgumentException("Invalid or duplicate product ID");
        }
        return Collections.unmodifiableSet(ids);
    }
    static void endpoint(String value) {
        try {
            URI uri=new URI(value);
            if(value.length()>2048 || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null || uri.getHost().isEmpty() ||
                uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null || uri.getPort()!=-1 && uri.getPort()!=443)
                throw new IllegalArgumentException("HTTPS purchase verification endpoint required");
        }catch(java.net.URISyntaxException failure){throw new IllegalArgumentException("Invalid purchase verification endpoint");}
    }
}
