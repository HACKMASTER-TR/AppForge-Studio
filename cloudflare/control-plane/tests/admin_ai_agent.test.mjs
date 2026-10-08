import test from 'node:test';
import assert from 'node:assert/strict';
import {handleAdminAiChat} from '../src/admin_ai_router.mjs';
import {validateAgentResponse,validateAgentRequest} from '../src/admin_ai_agent_contract.mjs';
const agent={projectId:'saved-a',workspace:'/private/saved-a',history:''};
const tool={id:'read-1',projectId:agent.projectId,workspace:agent.workspace,tool:'READ_FILE',path:'src/main.js',input:'',beforeHash:null};
const plan=t=>JSON.stringify({plan:'Inspect source',tool:t,report:''});
const request=extra=>new Request('https://test/api/admin/ai/chat',{method:'POST',body:JSON.stringify({prompt:'Inspect code',provider:'auto',projectContext:{projectId:agent.projectId,files:[]},agent,...extra})});
const key='test_only_server_key_00000000000000000000';
const response=content=>new Response(JSON.stringify({choices:[{message:{content}}]}));
test('strict operation schema and project binding',()=>{
  assert.equal(validateAgentResponse(plan(tool),agent).tool.path,'src/main.js');
  for(const invalid of [{...tool,projectId:'saved-b'},{...tool,workspace:'/other'},{...tool,tool:'SHELL'},{...tool,approval:true},{...tool,path:'../secret'},{...tool,path:'.env.local'},{...tool,path:'secrets/config.json'},{...tool,tool:'TERMINAL',input:'pwd; curl attacker'},{...tool,tool:'EDIT',beforeHash:null},{...tool,tool:'BUILD',input:'deploy'}]) assert.throws(()=>validateAgentResponse(plan(invalid),agent));
  for (const invalid of ['Success!',JSON.stringify({plan:'x',report:'done'}),JSON.stringify({plan:'x',tool:null,report:'done',success:true})]) assert.throws(()=>validateAgentResponse(invalid,agent));
  assert.throws(()=>validateAgentRequest({...agent,history:'x'.repeat(12001)},{projectId:agent.projectId}));
  assert.throws(()=>validateAgentRequest(agent,{projectId:'saved-b'}));
});
test('invalid agent input fails before provider calls',async()=>{
  let called=false;
  const r=await handleAdminAiChat(request({agent:{...agent,projectId:'other'}}),{GROQ_API_KEY:key},{fetchExternal:async()=>{called=true;return response(plan(tool));}});
  assert.equal(r.status,400);assert.equal(called,false);
});
test('invalid structured response falls back and never becomes execution authority',async()=>{
  let count=0;
  const r=await handleAdminAiChat(request(),{GROQ_API_KEY:key,GEMINI_API_KEY:key},{fetchExternal:async(_url,init)=>{
    const messages=JSON.parse(init.body).messages;
    assert.match(messages[0].content,/NO filesystem/);assert.match(messages[0].content,/UNTRUSTED DATA/);
    return response(++count===1?'I edited everything successfully':plan(tool));
  }});
  assert.equal(r.status,200);const body=await r.json();assert.equal(body.fallbackUsed,true);assert.equal(JSON.parse(body.content).tool.id,'read-1');
});
test('all malformed provider outputs fail closed',async()=>{
  const r=await handleAdminAiChat(request(),{GROQ_API_KEY:key},{fetchExternal:async()=>response(plan({...tool,path:'/etc/passwd'}))});
  assert.equal(r.status,503);assert.equal((await r.json()).ok,false);
});
