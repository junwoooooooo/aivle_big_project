const base=p=>`/api/v3/projects/${encodeURIComponent(p)}`;
export function createBusinessPersonaApi(client){return Object.freeze({
 planning:p=>client.get(`${base(p)}/planning/current`), runs:p=>client.get(`${base(p)}/module-runs`),
 prepare:(p,module,inputSnapshotId)=>client.post(`${base(p)}/module-handoffs`,{module,inputSnapshotId,requestedOperation:`START_${module}`}),
});}
