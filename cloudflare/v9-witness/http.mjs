// No public V9 witness interface until independent device enrollment,
// authentication, protected signing and external release review are complete.
export function handleHttp(){return new Response('V9 witness public API disabled',{
  status:503,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}
});}
