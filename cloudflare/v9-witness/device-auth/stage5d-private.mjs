// Stage 5D: internal, deny-by-default composition. No HTTP endpoint, no token minting API.
// Only a trusted Worker may construct this using independently configured private bindings.
import {demand} from './protocol.mjs';
import {V9DeviceChallengeStore} from './challenges.mjs';
import {V9DeviceEnrollmentRegistry} from './registry.mjs';
import {V9AdminNonceStore,V9GoogleAdminDecisionAuthority} from './admin-authority.mjs';
import {fetchGooglePublicKeys} from './admin-oidc.mjs';

export class V9PrivateDeviceAdminService {
  #registry; #nonces; #issuer; #challenges;
  constructor(storage, {adminSpkiDer,adminSpkiPin,signingKey,oauthClientId,allowedSubjects,
    getGoogleKeys=fetchGooglePublicKeys,clock=()=>Date.now()}={}) {
    demand(storage?.kv && typeof storage.transactionSync==='function','private DO storage required');
    demand(typeof oauthClientId==='string' && allowedSubjects instanceof Set && allowedSubjects.size>0,
      'trusted OIDC configuration required');
    demand(typeof getGoogleKeys==='function','trusted JWKS fetcher required');
    this.#registry=new V9DeviceEnrollmentRegistry(storage,{adminSpkiDer,adminSpkiPin,clock});
    this.#nonces=new V9AdminNonceStore(storage,{clock});
    this.#challenges=new V9DeviceChallengeStore(storage,{clock});
    this.#issuer=new V9GoogleAdminDecisionAuthority({signingKey,registry:this.#registry,
      nonces:this.#nonces,oauthClientId,allowedSubjects,getGoogleKeys,clock});
  }
  // Untrusted devices can only request an enrollment challenge and submit proof.
  async beginDeviceEnrollment({spkiB64Url}){
    return this.#registry.issueEnrollmentChallenge({spkiB64Url});
  }
  async submitDeviceEnrollment(proof){return this.#registry.requestEnrollment(proof);}
  // Never accept caller-supplied adminId, permitted key, JWKS, requestId or revision.
  beginAdminSession(){return this.#nonces.begin();}
  async decideDevice({idToken,sessionId,deviceId,action}) {
    const permit=await this.#issuer.authorizeAndSign({idToken,sessionId,deviceId,action});
    return this.#registry.applyAdminPermit(permit);
  }
  deviceStatus({deviceId}){return this.#registry.snapshot({deviceId});}
  beginAuthorizedChallenge({deviceId,operation}){
    return this.#challenges.issue({deviceId,operation});
  }
  verifyAuthorizedProof(proof){return this.#challenges.verifyAndConsume(proof);}
  // Do not expose a signer, permit, state mutation, raw storage or admin bypass.
}
