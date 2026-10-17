import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {execFileSync} from 'node:child_process';
import {billingStubs} from './helpers/fast_html_billing_stubs.js';
import {mediaStubs} from './helpers/fast_html_media_stubs.js';
const root=path.resolve(import.meta.dirname,'../..');

test('production Billing controller uses SDK readiness/offers and never completes pending, unverified or unacknowledged purchases',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'appforge-billing-'));
 try {
 const stubs={...billingStubs,'org/json/JSONObject.java':mediaStubs['org/json/JSONObject.java']};
 const files=Object.entries(stubs).map(([name,source])=>{const file=path.join(dir,name);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,source);return file;});
 const harness=path.join(dir,'BillingCheck.java');fs.writeFileSync(harness,`package com.appforge.htmlstub;
import android.app.*;import android.os.*;import com.android.billingclient.api.*;import org.json.*;import java.util.*;
public class BillingCheck {
 static List<String> events=new ArrayList<>();static AppForgeBilling billing;static boolean foreground=true;static BillingClient sdk;
 static void check(boolean v){if(!v)throw new AssertionError(events.toString());}
 interface Action{void run();}static void denied(Action a){try{a.run();throw new AssertionError("Expected denial");}catch(IllegalStateException|IllegalArgumentException|SecurityException expected){}}
 static boolean has(String state){return events.stream().anyMatch(e->e.contains((char)34+state+String.valueOf((char)34)));}
 static void setup(){
  billing=new AppForgeBilling(new Activity(),new JSONObject(),()->foreground,v->events.add(v.toString()));
  billing.network(false);check(has("OFFLINE"));denied(()->billing.purchase("premium","offer-one"));
  billing.network(true);sdk=BillingClient.last;check(has("CONNECTING")&&!sdk.ready);denied(()->billing.query());
  sdk.ready=true;sdk.connection.onBillingSetupFinished(new BillingResult(0));Handler.drain();check(has("READY"));
  sdk.queries.get("inapp").onProductDetailsResponse(new BillingResult(0),new QueryProductDetailsResult(List.of(new ProductDetails("coins","inapp"),new ProductDetails("premium","inapp"))));
  sdk.queries.get("subs").onProductDetailsResponse(new BillingResult(0),new QueryProductDetailsResult(List.of(new ProductDetails("monthly","subs"))));Handler.drain();check(has("PRODUCTS"));
 }
 static void purchase(Purchase p){sdk.purchases.onPurchasesUpdated(new BillingResult(0),List.of(p));Handler.drain();}
 static void awaitState(String state)throws Exception {for(int i=0;i<400&&!has(state);i++){Handler.drain();Thread.sleep(5);}Handler.drain();check(has(state));}
 public static void main(String[] args)throws Exception {
 setup();check(billing.query().equals("REQUESTED"));foreground=false;denied(()->billing.purchase("premium","offer-one"));foreground=true;
 denied(()->billing.purchase("unknown","offer-one"));denied(()->billing.purchase("monthly","wrong-offer"));check(sdk.flows==0);
 check(billing.purchase("premium","offer-one").equals("FLOW_OPENED"));check(!has("COMPLETE"));denied(()->billing.purchase("premium","offer-one"));
 sdk.purchases.onPurchasesUpdated(new BillingResult(1),null);Handler.drain();check(has("CANCELED")&&sdk.acks==0&&sdk.consumes==0);
 Purchase pending=new Purchase("coins","pending-token");pending.state=2;purchase(pending);check(has("PENDING")&&sdk.acks==0&&sdk.consumes==0);
 Purchase suspended=new Purchase("premium","suspended-token");suspended.suspended=true;purchase(suspended);check(has("SUSPENDED"));
 Purchase foreign=new Purchase("premium","foreign-token");foreign.pkg="com.foreign";purchase(foreign);check(has("RECEIPT_UNSUPPORTED"));
 AppForgePurchaseVerifier.accepted=false;purchase(new Purchase("premium","unverified-token"));awaitState("VERIFICATION_FAILED");check(sdk.acks==0&&sdk.consumes==0&&!has("COMPLETE"));
 AppForgePurchaseVerifier.accepted=true;purchase(new Purchase("premium","ack-token"));for(int i=0;i<400&&sdk.ack==null;i++){Handler.drain();Thread.sleep(5);}check(sdk.ack!=null&&!has("COMPLETE"));
 sdk.ack.onAcknowledgePurchaseResponse(new BillingResult(5));Handler.drain();check(has("ACKNOWLEDGE_FAILED")&&!has("COMPLETE"));
 purchase(new Purchase("premium","verified-token"));for(int i=0;i<400&&!"verified-token".equals(sdk.ackToken);i++){Handler.drain();Thread.sleep(5);}check("verified-token".equals(sdk.ackToken));sdk.ack.onAcknowledgePurchaseResponse(new BillingResult(0));Handler.drain();check(has("COMPLETE"));
 check(events.stream().noneMatch(e->e.contains("verified-token")||e.contains("ack-token")||e.contains("unverified-token")));
 int before=sdk.acks;purchase(new Purchase("premium","verified-token"));Handler.drain();check(sdk.acks==before);
 events.clear();Purchase coins=new Purchase("coins","consumable-token");coins.quantity=3;purchase(coins);for(int i=0;i<400&&sdk.consumed==null;i++){Handler.drain();Thread.sleep(5);}check(sdk.consumed!=null&&AppForgePurchaseVerifier.quantity==3&&!has("COMPLETE"));
 sdk.consumed.onConsumeResponse(new BillingResult(0),"wrong-token");Handler.drain();check(has("CONSUME_FAILED")&&!has("COMPLETE"));
 Purchase validCoins=new Purchase("coins","valid-consumable-token");purchase(validCoins);for(int i=0;i<400&&!"valid-consumable-token".equals(sdk.consumeToken);i++){Handler.drain();Thread.sleep(5);}sdk.consumed.onConsumeResponse(new BillingResult(0),"valid-consumable-token");Handler.drain();check(has("COMPLETE"));
 events.clear();AppForgePurchaseVerifier.entered=new java.util.concurrent.CountDownLatch(1);AppForgePurchaseVerifier.release=new java.util.concurrent.CountDownLatch(1);purchase(new Purchase("premium","offline-token"));check(AppForgePurchaseVerifier.entered.await(2,java.util.concurrent.TimeUnit.SECONDS));
 BillingClient old=sdk;int oldAcks=old.acks;billing.network(false);AppForgePurchaseVerifier.release.countDown();Thread.sleep(30);Handler.drain();check(old.ended&&old.acks==oldAcks&&!has("COMPLETE"));denied(()->billing.refresh());
 old.connection.onBillingSetupFinished(new BillingResult(0));Handler.drain();check(!has("COMPLETE"));
 AppForgePurchaseVerifier.entered=null;AppForgePurchaseVerifier.release=null;billing.network(true);sdk=BillingClient.last;check(sdk!=old&&!sdk.ready);sdk.ready=true;sdk.connection.onBillingSetupFinished(new BillingResult(0));Handler.drain();
 sdk.ready=false;sdk.connection.onBillingServiceDisconnected();Handler.drain();check(has("DISCONNECTED"));denied(()->billing.query());billing.network(true);check(BillingClient.last!=sdk);
 billing.close();check(BillingClient.last.ended);int count=events.size();BillingClient.last.connection.onBillingSetupFinished(new BillingResult(0));Handler.drain();check(events.size()==count);denied(()->billing.query());System.out.println("BILLING CONTROLLER PASS");
 }
}`);
 execFileSync('javac',['-d',dir,...files,harness,path.join(root,'android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeBilling.java')],{stdio:'pipe',timeout:60000});
 assert.match(execFileSync('java',['-cp',dir,'com.appforge.htmlstub.BillingCheck'],{encoding:'utf8',timeout:30000}),/BILLING CONTROLLER PASS/);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
