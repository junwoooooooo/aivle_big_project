import { useCallback, useEffect, useMemo, useState } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createPlanningApi } from '../api/planningApi.js';
export default function usePlanningRevision(projectId) {
 const client=useApiClient(); const api=useMemo(()=>createPlanningApi(client),[client]);
 const [state,setState]=useState({loading:true,current:null,error:null,busyId:null,finalizing:false});
 const refresh=useCallback(async()=>{try{const p=await api.current(projectId);setState(v=>({...v,loading:false,current:p.data,error:null}));}catch(error){setState(v=>({...v,loading:false,error}));}},[api,projectId]);
 useEffect(()=>{const t=setTimeout(refresh,0);return()=>clearTimeout(t);},[refresh]);
 const decide=async(id,action,modified)=>{setState(v=>({...v,busyId:id,error:null}));try{const p=await api.decide(projectId,id,action,modified);setState(v=>({...v,current:p.data,busyId:null}));}catch(error){setState(v=>({...v,busyId:null,error}));}};
 const finalize=async()=>{setState(v=>({...v,finalizing:true,error:null}));try{await api.finalize(projectId);await refresh();setState(v=>({...v,finalizing:false}));}catch(error){setState(v=>({...v,finalizing:false,error}));}};
 return {...state,refresh,decide,finalize};
}
