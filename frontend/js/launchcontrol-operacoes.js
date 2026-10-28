(() => {
  "use strict";
  const $ = selector => document.querySelector(selector);
  const sessionUser = () => typeof currentUser !== "undefined" ? currentUser : null;
  const money = value => new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(Number(value||0));
  const dateTime = value => value ? new Intl.DateTimeFormat("pt-BR",{dateStyle:"short",timeStyle:"short"}).format(new Date(String(value).replace(" ","T"))) : "—";
  const safe = value => String(value ?? "");
  async function api(path, options={}) {
    const headers = new Headers(options.headers || {});
    headers.set("Accept","application/json");
    if(options.method && options.method.toUpperCase()!=="GET") headers.set("X-CSRF-Token",sessionUser()?.csrf || "");
    const response=await fetch(path,{...options,headers});
    const payload=await response.json().catch(()=>({erro:"Resposta inválida do servidor."}));
    if(!response.ok) throw new Error(payload.erro || "A operação não foi concluída.");
    return payload;
  }
  function status(node,message,error=false){if(node){node.textContent=message;node.dataset.state=error?"error":"ok";}}
  function element(tag,className,text){const node=document.createElement(tag);if(className)node.className=className;if(text!=null)node.textContent=text;return node;}
  function setVisible(selector,visible){const node=$(selector);if(node)node.hidden=!visible;}
  async function loadAnalytics(){
    const target=$("#operations-metrics"); if(!target)return;
    target.replaceChildren();
    if(!sessionUser()){target.append(element("p","muted","Entre para visualizar indicadores vinculados à sua conta."));return;}
    try{
      const data=await api("api/dashboard/analytics");
      const items=[
        ["Ordens de serviço",data.ordens],["Ordens abertas",data.ordensAbertas],
        ["Ordens concluídas",data.ordensConcluidas],["Orçamentos",data.orcamentos],
        ["Aguardando decisão",data.orcamentosPendentes],["Valor concluído",money(data.receitaConcluida)],
        ["Agendamentos",data.agendamentos]
      ];
      items.forEach(([label,value])=>{const card=element("article","stat-card");card.append(element("p",null,label),element("strong",null,safe(value)),element("small",null,"Dados registrados no sistema"));target.append(card);});
      status($("#operations-dashboard-status"),"Indicadores atualizados · "+new Date().toLocaleTimeString("pt-BR"));
    }catch(e){status($("#operations-dashboard-status"),e.message,true);}
  }
  async function loadOffices(){
    const select=$("#appointment-office");if(!select)return;
    try{
      const offices=await api("api/agendamentos?oficinas=1");
      select.replaceChildren(new Option("Selecione a oficina",""));
      offices.forEach(item=>{const location=[item.cidade,item.estado].filter(Boolean).join(" · ");select.add(new Option(item.nome+(location?" — "+location:""),item.id));});
      const adminOffice=$("#schedule-office-id");
      if(adminOffice){adminOffice.replaceChildren(new Option("Selecione a oficina",""));offices.forEach(item=>adminOffice.add(new Option(item.nome,item.id)));}
    }catch(e){status($("#appointment-status"),e.message,true);}
  }
  async function loadVehicles(){
    const select=$("#appointment-vehicle");if(!select||!sessionUser()||sessionUser().perfil!=="CLIENTE")return;
    try{const vehicles=await api("api/veiculos");select.replaceChildren(new Option("Selecione o veículo",""));vehicles.forEach(v=>select.add(new Option([v.marca,v.modelo,v.ano].filter(Boolean).join(" "),v.id)));}
    catch(e){status($("#appointment-status"),e.message,true);}
  }
  async function loadAppointments(){
    const target=$("#appointment-list");if(!target)return;target.replaceChildren();
    if(!sessionUser()){target.append(element("p","muted","Entre para consultar ou solicitar agendamentos."));return;}
    try{
      const rows=await api("api/agendamentos");
      if(!rows.length){target.append(element("p","muted","Ainda não há agendamentos registrados."));return;}
      rows.forEach(item=>{
        const card=element("article","request-item");card.append(element("strong",null,item.oficina+" · "+item.codigo));
        card.append(element("p",null,dateTime(item.inicio)+" — "+dateTime(item.fim)+" · "+item.veiculo+" · "+item.status));
        if(["AGENDADO","CONFIRMADO"].includes(item.status)){
          const cancel=element("button","mini-button","Cancelar");cancel.type="button";
          cancel.addEventListener("click",async()=>{if(!confirm("Cancelar este agendamento?"))return;try{await api("api/agendamentos",{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8"},body:new URLSearchParams({acao:"cancelar",id:item.id})});await loadAppointments();status($("#appointment-status"),"Agendamento cancelado.");}catch(e){status($("#appointment-status"),e.message,true);}});
          card.append(cancel);
        }
        target.append(card);
      });
    }catch(e){target.append(element("p","muted",e.message));}
  }
  async function loadAudit(){
    const target=$("#audit-list");if(!target)return;target.replaceChildren();
    if(!sessionUser()){target.append(element("p","muted","Entre para consultar eventos de auditoria disponíveis ao seu perfil."));return;}
    try{
      const rows=await api("api/auditoria");
      if(!rows.length){target.append(element("p","muted","Nenhum evento de auditoria disponível para este perfil."));return;}
      rows.forEach(row=>{const card=element("article","request-item");card.append(element("strong",null,row.acao+" · "+row.entidade+(row.entidadeId?" #"+row.entidadeId:"")));card.append(element("p",null,dateTime(row.criadoEm)+" · "+(row.responsavel||"Sistema")));if(row.detalhes)card.append(element("p",null,row.detalhes));target.append(card);});
    }catch(e){target.append(element("p","muted",e.message));}
  }
  async function loadPreferences(){
    if(!sessionUser())return;
    try{const p=await api("api/notificacoes/preferencias");$("#notification-email").checked=Boolean(p.emailAtivo);$("#notification-whatsapp").checked=Boolean(p.whatsappAtivo);$("#notification-phone").value=p.whatsappNumero||"";$("#notification-email-address").textContent=p.email||"E-mail não cadastrado";}
    catch(e){status($("#notification-preferences-status"),e.message,true);}
  }
  function applyFeatureRole(){
    const role=sessionUser()?.perfil||"";
    setVisible("#operations-panel",["CLIENTE","OFICINA","ADMINISTRADOR"].includes(role));
    setVisible("#appointment-panel",["CLIENTE","OFICINA","ADMINISTRADOR"].includes(role));
    setVisible("#fitment-import-panel",role==="ADMINISTRADOR");
    setVisible("#audit-panel",["CLIENTE","OFICINA","ADMINISTRADOR"].includes(role));
    setVisible("#notification-preferences-panel",Boolean(sessionUser()));
    setVisible("#appointment-create-form",role==="CLIENTE");
    setVisible("#appointment-config-form",role==="OFICINA"||role==="ADMINISTRADOR");
    setVisible("#schedule-office-wrap",role==="ADMINISTRADOR");
    if($("#schedule-office-id")) $("#schedule-office-id").required=role==="ADMINISTRADOR";
    setVisible("#appointment-config-note",role==="OFICINA");
  }
  function wireForms(){
    $("#appointment-create-form")?.addEventListener("submit",async event=>{
      event.preventDefault();const form=new FormData(event.currentTarget);
      status($("#appointment-status"),"Validando capacidade e reservando horário…");
      try{const result=await api("api/agendamentos",{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8"},body:new URLSearchParams(Object.fromEntries(form))});status($("#appointment-status"),"Agendamento "+result.codigo+" criado.");event.currentTarget.reset();await Promise.all([loadAppointments(),loadAnalytics()]);}
      catch(e){status($("#appointment-status"),e.message,true);}
    });
    $("#appointment-config-form")?.addEventListener("submit",async event=>{
      event.preventDefault();const form=new FormData(event.currentTarget);status($("#appointment-config-status"),"Salvando expediente…");
      try{await api("api/agendamentos",{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8"},body:new URLSearchParams({acao:"configurar",diaSemana:form.get("diaSemana"),horaInicio:form.get("horaInicio"),horaFim:form.get("horaFim"),capacidade:form.get("capacidade"),oficinaId:form.get("oficinaId")||""})});status($("#appointment-config-status"),"Expediente e capacidade salvos.");}
      catch(e){status($("#appointment-config-status"),e.message,true);}
    });
    $("#fitment-import-file")?.addEventListener("change",async event=>{
      const file=event.currentTarget.files?.[0];if(!file)return;
      if(file.size>500000){status($("#fitment-import-status"),"O arquivo ultrapassa 500 KB.",true);return;}
      $("#fitment-import-csv").value=await file.text();status($("#fitment-import-status"),"CSV carregado. Confira o cabeçalho e importe.");
    });
    $("#fitment-import-form")?.addEventListener("submit",async event=>{
      event.preventDefault();const csv=$("#fitment-import-csv").value;status($("#fitment-import-status"),"Validando CSV; nenhuma linha será salva se houver erro…");
      try{const result=await api("api/compatibilidade-pecas/importacao",{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8"},body:new URLSearchParams({csv})});status($("#fitment-import-status"),result.importadas+" referências importadas com sucesso.");event.currentTarget.reset();}
      catch(e){status($("#fitment-import-status"),e.message,true);}
    });
    $("#notification-preferences-form")?.addEventListener("submit",async event=>{
      event.preventDefault();const body=new URLSearchParams({emailAtivo:String($("#notification-email").checked),whatsappAtivo:String($("#notification-whatsapp").checked),whatsappNumero:$("#notification-phone").value});
      status($("#notification-preferences-status"),"Salvando preferências…");
      try{await api("api/notificacoes/preferencias",{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8"},body});status($("#notification-preferences-status"),"Preferências salvas. A entrega externa depende das credenciais do servidor.");}
      catch(e){status($("#notification-preferences-status"),e.message,true);}
    });
  }
  async function refresh(){
    applyFeatureRole();
    if(!sessionUser())return;
    await Promise.allSettled([loadAnalytics(),loadOffices(),loadVehicles(),loadAppointments(),loadAudit(),loadPreferences()]);
  }
  document.addEventListener("DOMContentLoaded",()=>{
    wireForms();
    $("#operations-refresh")?.addEventListener("click",()=>refresh());
    $("#audit-refresh")?.addEventListener("click",()=>loadAudit());
    const previousRefresh=$("#refresh-button");
    previousRefresh?.addEventListener("click",()=>{window.setTimeout(refresh,200);});
    applyFeatureRole();
    window.setTimeout(refresh,800);
    window.addEventListener("launchcontrol:session",refresh);
  });
})();
