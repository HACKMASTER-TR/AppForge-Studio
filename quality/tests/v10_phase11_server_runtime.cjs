// Opt-in: exact installed RN CLI fixture. In-memory streams; never launches an editor.
const assert = require('node:assert/strict');
const path = require('node:path');
const {Readable} = require('node:stream');
const {createRequire} = require('node:module');
if (!process.argv[2]) throw Error('exact installed CLI fixture required');
const req = createRequire(path.resolve(process.argv[2], 'package.json'));
const api = req.resolve('@react-native-community/cli-server-api');
const local = createRequire(api);
assert.equal(local('body-parser/package.json').version, '2.4.0');
const stack = local('./openStackFrameMiddleware').default({});
async function malformed(body, expected, encoding) {
  const input = Readable.from([Buffer.from(body)]);
  input.method = 'POST'; input.url = '/'; input.httpVersion = '1.1';
  input.headers = {'content-type':'application/json', 'content-length':Buffer.byteLength(body)};
  if (encoding) input.headers['content-encoding'] = encoding;
  const result = await new Promise((resolve,reject) => {
    const timer = setTimeout(()=>reject(Error('middleware timeout')), 2000);
    stack(input, {setHeader(){}, end(){reject(Error('invalid body reached handler'));}}, error => {
      clearTimeout(timer); resolve(error);
    });
  });
  assert.equal(result.status, expected);
}
(async()=>{
  const parser=local('body-parser').json();
  const input=Readable.from([Buffer.from('{"ordinary":42}')]);
  input.method='POST';input.headers={'content-type':'application/json','content-length':15};
  await new Promise((resolve,reject)=>parser(input,{},e=>e?reject(e):resolve()));
  assert.deepEqual(input.body,{ordinary:42});
  await malformed('{',400);
  await malformed('"primitive"',400);
  await malformed('{"x":"'+'a'.repeat(103000)+'"}',413);
  await malformed('{}',415,'unsupported');
  const {middleware,websocketEndpoints}=local(api).createDevServerMiddleware({watchFolders:[],port:8081});
  let output;
  const response=new (require('node:http').ServerResponse)({method:'GET'});
  response.end=function(v){output=v;this.emit('probe-end');};
  await new Promise((resolve,reject)=>{
    response.once('probe-end',resolve);
    middleware({method:'GET',url:'/status',headers:{},socket:{}},response,reject);
  });
  assert.equal(output,'packager-status:running');
  for(const server of Object.values(websocketEndpoints)) server.close();
  console.log('actual CLI dev-server status; body-parser valid JSON, malformed, primitive, oversize, unsupported encoding PASS; native runtime NOT_RUN');
})().catch(e=>{console.error(e);process.exitCode=1;});
