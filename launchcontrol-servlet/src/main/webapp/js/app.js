const rows = document.querySelector("#service-rows");
const serviceStatus = document.querySelector("#service-status");
const apiStatus = document.querySelector("#api-status");
const searchInput = document.querySelector("#service-search");
const dialog = document.querySelector("#detail-dialog");
const detailsTitle = document.querySelector("#detail-title");
const detailsContent = document.querySelector("#detail-content");
const authDialog = document.querySelector("#auth-dialog");
const quoteEditDialog = document.querySelector("#quote-edit-dialog");
let authMode = "login";
let currentUser = null;
let orders = [];

const statusLabels = {
  CONCLUIDO: "Concluído",
  EM_ANDAMENTO: "Em andamento",
  AGUARDANDO_PECA: "Aguardando peça",
  PENDENTE: "Pendente",
  AGUARDANDO_ANALISE: "Aguardando análise"
};
const currency = value => value == null ? "—" : Number(value).toLocaleString("pt-BR", {style:"currency",currency:"BRL"});

function renderOrders() {
  const term = searchInput.value.trim().toLocaleLowerCase("pt-BR");
  const filtered = orders.filter(order =>
    [order.codigo, order.cliente, order.veiculo, order.servico, statusLabels[order.status] || order.status]
      .some(value => String(value || "").toLocaleLowerCase("pt-BR").includes(term))
  );
  rows.replaceChildren();
  if (!filtered.length) {
    const tr = document.createElement("tr");
    const td = document.createElement("td");
    td.colSpan = 7;
    td.className = "empty";
    td.textContent = orders.length ? "Nenhuma ordem corresponde à pesquisa." : "Nenhuma ordem encontrada no banco de dados.";
    tr.append(td); rows.append(tr); return;
  }
  for (const order of filtered) {
    const tr = document.createElement("tr");
    const values = [
      {text: order.codigo || "#" + order.id, className:"order-id"},
      {text: order.cliente || "—"},
      {text: order.veiculo || "—"},
      {text: order.servico || "—"}
    ];
    for (const value of values) {
      const td = document.createElement("td"); td.textContent = value.text;
      if (value.className) td.className = value.className;
      tr.append(td);
    }
    const statusCell = document.createElement("td");
    const badge = document.createElement("span");
    badge.className = "status-badge " + String(order.status || "PENDENTE").toLowerCase();
    badge.textContent = statusLabels[order.status] || order.status || "Pendente";
    statusCell.append(badge); tr.append(statusCell);
    const price = document.createElement("td"); price.textContent = currency(order.valor); tr.append(price);
    const actions = document.createElement("td");
    const group = document.createElement("div"); group.className = "action-group";
    const view = document.createElement("button"); view.type = "button"; view.className = "mini-button"; view.textContent = "Visualizar";
    view.addEventListener("click", () => showDetails(order));
    group.append(view);
    const completed = order.status === "CONCLUIDO";
    const canEditQuote = !completed && ["OFICINA", "ADMINISTRADOR"].includes(currentUser?.perfil)
      && order.orcamentoId && ["PENDENTE", "ENVIADO"].includes(order.orcamentoStatus);
    if (canEditQuote) {
      const edit = document.createElement("button"); edit.type = "button"; edit.className = "mini-button"; edit.textContent = "Editar orçamento";
      edit.addEventListener("click", () => {
        const form = document.querySelector("#quote-edit-form");
        form.elements.id.value = order.orcamentoId;
        form.elements.valor.value = order.valor ?? "";
        form.elements.prazoDias.value = order.prazoDias ?? 0;
        form.elements.observacoes.value = order.observacoes ?? "";
        document.querySelector("#quote-edit-status").textContent = "Editando orçamento #" + order.orcamentoId + ".";
        quoteEditDialog.showModal();
      });
      group.append(edit);
    }
    if (!completed && ["OFICINA", "ADMINISTRADOR"].includes(currentUser?.perfil)) {
      const statusSelect = document.createElement("select"); statusSelect.className = "mini-button"; statusSelect.setAttribute("aria-label", "Novo status da ordem");
      const placeholder = document.createElement("option"); placeholder.value = ""; placeholder.textContent = "Status…"; statusSelect.append(placeholder);
      for (const statusValue of ["EM_ANDAMENTO", "AGUARDANDO_PECA", "CONCLUIDO"]) {
        const option = document.createElement("option"); option.value = statusValue; option.textContent = statusLabels[statusValue] || statusValue; statusSelect.append(option);
      }
      const updateStatus = document.createElement("button"); updateStatus.type = "button"; updateStatus.className = "mini-button"; updateStatus.textContent = "Atualizar";
      updateStatus.addEventListener("click", async () => {
        if (!statusSelect.value) { statusSelect.focus(); return; }
        updateStatus.disabled = true;
        try {
          const response = await fetch("api/ordens-servico", {method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","X-CSRF-Token":currentUser?.csrf || ""},body:new URLSearchParams({id:order.id,status:statusSelect.value})});
          const result = await response.json();
          if (!response.ok) throw new Error(result.erro || "Não foi possível atualizar status.");
          await loadOrders();
        } catch (error) { alert(error.message); updateStatus.disabled = false; }
      });
      group.append(statusSelect, updateStatus);
    }
    actions.append(group); tr.append(actions); rows.append(tr);
  }
}

function showDetails(order) {
  detailsTitle.textContent = order.codigo || "Ordem #" + order.id;
  detailsContent.textContent = JSON.stringify({
    cliente: order.cliente, veiculo: order.veiculo, servico: order.servico,
    status: statusLabels[order.status] || order.status, valor: currency(order.valor),
    inicio: order.inicio || null, previsao: order.previsao || null
  }, null, 2);
  dialog.showModal();
}

async function loadOrders() {
  serviceStatus.textContent = "Consultando endpoint Java Servlet…";
  try {
    const response = await fetch("api/ordens-servico", {headers: {"Accept":"application/json"}});
    if (response.status === 401) throw new Error("Faça login no backend para consultar as ordens. Nenhuma sessão autenticada foi encontrada.");
    if (!response.ok) throw new Error("A API respondeu com HTTP " + response.status + ".");
    const data = await response.json();
    orders = Array.isArray(data) ? data : [];
    apiStatus.textContent = "API conectada";
    apiStatus.classList.add("connected");
    document.querySelector("#stat-total").textContent = orders.length;
    document.querySelector("#stat-progress").textContent = orders.filter(x => x.status === "EM_ANDAMENTO").length;
    document.querySelector("#stat-complete").textContent = orders.filter(x => x.status === "CONCLUIDO").length;
    document.querySelector("#stat-parts").textContent = orders.filter(x => x.status === "AGUARDANDO_PECA").length;
    serviceStatus.textContent = "Dados recebidos do backend; os registros não vêm de dados mockados no navegador.";
    renderOrders();
  } catch (error) {
    orders = [];
    apiStatus.textContent = "API indisponível";
    apiStatus.classList.remove("connected");
    document.querySelectorAll("#stat-total,#stat-progress,#stat-complete,#stat-parts").forEach(el => el.textContent = "—");
    rows.innerHTML = '<tr><td colspan="7" class="empty"></td></tr>';
    rows.querySelector(".empty").textContent = error.message || "Não foi possível consultar a API.";
    serviceStatus.textContent = "Configure o servidor Jakarta Servlet e a conexão MySQL. Não exibimos dados de exemplo como se fossem reais.";
  }
}


function applyRoleUI() {
  const role = currentUser?.perfil;
  document.querySelector("#customer-tools").hidden = role !== "CLIENTE";
  document.querySelector("#office-tools").hidden = role !== "OFICINA";
  document.querySelector("#quote-panel").hidden = !["CLIENTE", "OFICINA", "ADMINISTRADOR"].includes(role);
}

async function loadVehicles() {
  if (!currentUser || currentUser.perfil !== "CLIENTE") return;
  const select = document.querySelector("#vehicle-select");
  try {
    const response = await fetch("api/veiculos");
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível carregar veículos.");
    select.replaceChildren();
    const placeholder = document.createElement("option");
    placeholder.value = ""; placeholder.textContent = result.length ? "Selecione um veículo" : "Cadastre seu primeiro veículo";
    select.append(placeholder);
    result.forEach(vehicle => {
      const option = document.createElement("option");
      option.value = vehicle.id;
      option.textContent = vehicle.id + " · " + vehicle.marca + " " + vehicle.modelo + (vehicle.ano ? " (" + vehicle.ano + ")" : "");
      select.append(option);
    });
    document.querySelector("#vehicle-status").textContent = result.length + (result.length === 1 ? " veículo carregado." : " veículos carregados.");
  } catch (error) {
    document.querySelector("#vehicle-status").textContent = error.message;
  }
}

async function loadRequests() {
  if (!currentUser || currentUser.perfil !== "CLIENTE") return;
  const list = document.querySelector("#request-list");
  list.replaceChildren();
  try {
    const response = await fetch("api/solicitacoes");
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível consultar solicitações.");
    if (!result.length) {
      const empty = document.createElement("p"); empty.className = "muted"; empty.textContent = "Você ainda não tem solicitações."; list.append(empty); return;
    }
    result.slice(0, 5).forEach(item => {
      const card = document.createElement("div"); card.className = "request-item";
      const title = document.createElement("strong"); title.textContent = "Solicitação #" + item.id;
      const description = document.createElement("p"); description.textContent = item.descricao;
      const status = document.createElement("span"); status.className = "status-badge " + String(item.status).toLowerCase(); status.textContent = item.status;
      card.append(title, description, status); list.append(card);
    });
  } catch (error) {
    const message = document.createElement("p"); message.className = "form-status"; message.textContent = error.message; list.append(message);
  }
}


async function loadQuotes() {
  const list = document.querySelector("#quote-list");
  if (!currentUser || !["CLIENTE", "OFICINA", "ADMINISTRADOR"].includes(currentUser.perfil)) {
    list.replaceChildren(); return;
  }
  list.replaceChildren();
  try {
    const response = await fetch("api/orcamentos");
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível consultar orçamentos.");
    if (!result.length) {
      const empty = document.createElement("p"); empty.className = "muted"; empty.textContent = "Nenhum orçamento encontrado para esta conta."; list.append(empty); return;
    }
    result.forEach(quote => {
      const card = document.createElement("article"); card.className = "quote-card";
      const title = document.createElement("strong"); title.textContent = "Orçamento #" + quote.id + " · Solicitação #" + quote.solicitacaoId;
      const description = document.createElement("p"); description.textContent = quote.descricao || "Serviço automotivo";
      const meta = document.createElement("p"); meta.textContent = (quote.oficina || "Oficina") + " · " + currency(quote.valor) + " · Prazo: " + (quote.prazoDias == null ? "a combinar" : quote.prazoDias + " dia(s)");
      const badge = document.createElement("span"); badge.className = "status-badge " + String(quote.status).toLowerCase(); badge.textContent = quote.status;
      card.append(title, description, meta, badge);
      if (currentUser.perfil === "OFICINA" && ["PENDENTE", "ENVIADO"].includes(quote.status)) {
        const edit = document.createElement("button"); edit.type = "button"; edit.className = "mini-button"; edit.textContent = "Editar orçamento";
        edit.addEventListener("click", () => {
          const form = document.querySelector("#quote-edit-form");
          form.elements.id.value = quote.id;
          form.elements.valor.value = quote.valor;
          form.elements.prazoDias.value = quote.prazoDias ?? 0;
          form.elements.observacoes.value = quote.observacoes ?? "";
          document.querySelector("#quote-edit-status").textContent = "Editando orçamento #" + quote.id + ".";
          quoteEditDialog.showModal();
        });
        const editRow = document.createElement("div"); editRow.className = "quote-actions"; editRow.append(edit); card.append(editRow);
      }
      const canDecide = currentUser.perfil === "CLIENTE" && ["ENVIADO", "PENDENTE"].includes(quote.status) && !["ACEITA", "CONCLUIDA", "CANCELADA"].includes(quote.solicitacaoStatus);
      if (canDecide) {
        const actions = document.createElement("div"); actions.className = "action-group quote-actions";
        for (const decision of ["ACEITAR", "RECUSAR"]) {
          const button = document.createElement("button"); button.type = "button"; button.className = decision === "ACEITAR" ? "button button-primary" : "mini-button";
          button.textContent = decision === "ACEITAR" ? "Aceitar orçamento" : "Recusar";
          button.addEventListener("click", async () => {
            button.disabled = true;
            try {
              const res = await fetch("api/orcamentos/decisao", {method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","X-CSRF-Token":currentUser?.csrf || ""},body:new URLSearchParams({orcamentoId:quote.id,decisao:decision})});
              const data = await res.json();
              if (!res.ok) throw new Error(data.erro || "Não foi possível registrar a decisão.");
              await loadQuotes(); await loadOrders();
              const formStatus = document.querySelector("#checkout-status");
              if (decision === "ACEITAR") formStatus.textContent = "Orçamento aceito. Use o ID #" + quote.id + " no checkout demonstrativo; nenhum pagamento será cobrado.";
            } catch (error) { alert(error.message); button.disabled = false; }
          });
          actions.append(button);
        }
        card.append(actions);
      }
      list.append(card);
    });
  } catch (error) {
    const message = document.createElement("p"); message.className = "form-status"; message.textContent = error.message; list.append(message);
  }
}

async function loadOfficeRequests() {
  if (!currentUser || currentUser.perfil !== "OFICINA") return;
  const list = document.querySelector("#office-request-list");
  list.replaceChildren();
  try {
    const response = await fetch("api/solicitacoes");
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível consultar solicitações.");
    if (!result.length) {
      const empty = document.createElement("p"); empty.className = "muted"; empty.textContent = "Não há solicitações abertas no momento."; list.append(empty); return;
    }
    result.forEach(item => {
      const card = document.createElement("div"); card.className = "request-item";
      const title = document.createElement("strong"); title.textContent = "Solicitação #" + item.id + " · " + (item.cliente || "Cliente");
      const desc = document.createElement("p"); desc.textContent = (item.veiculo || "Veículo não informado") + " — " + item.descricao;
      const use = document.createElement("button"); use.type = "button"; use.className = "mini-button"; use.textContent = "Usar esta solicitação";
      use.addEventListener("click", () => { document.querySelector("#quote-form [name=solicitacaoId]").value = item.id; document.querySelector("#quote-form [name=valor]").focus(); });
      card.append(title, desc, use); list.append(card);
    });
  } catch (error) {
    const message = document.createElement("p"); message.className = "form-status"; message.textContent = error.message; list.append(message);
  }
}


async function loadPayments() {
  const list = document.querySelector("#payment-history");
  if (!currentUser || currentUser.perfil !== "CLIENTE") {
    list.textContent = "Entre como cliente para consultar pagamentos.";
    return;
  }
  list.replaceChildren();
  try {
    const response = await fetch("api/pagamentos");
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível consultar pagamentos.");
    if (!result.length) { list.textContent = "Nenhum pagamento registrado."; return; }
    result.forEach(payment => {
      const card = document.createElement("div"); card.className = "payment-item";
      const title = document.createElement("strong"); title.textContent = "Pagamento #" + payment.id + " · Pedido #" + payment.pedidoId;
      const detail = document.createElement("p"); detail.textContent = currency(payment.valor) + " · " + payment.metodo + " · " + payment.modo;
      const status = document.createElement("span"); status.className = "status-badge " + String(payment.status).toLowerCase(); status.textContent = payment.status;
      card.append(title, detail, status);
      if (payment.status === "PENDENTE" && payment.modo === "DEMONSTRACAO") {
        const cancel = document.createElement("button"); cancel.type = "button"; cancel.className = "mini-button"; cancel.textContent = "Cancelar demonstração";
        cancel.addEventListener("click", async () => {
          cancel.disabled = true;
          try {
            const res = await fetch("api/pagamentos", {method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","X-CSRF-Token":currentUser?.csrf || ""},body:new URLSearchParams({id:payment.id})});
            const data = await res.json();
            if (!res.ok) throw new Error(data.erro || "Não foi possível cancelar pagamento.");
            await loadPayments();
          } catch (error) { alert(error.message); cancel.disabled = false; }
        });
        card.append(cancel);
      }
      list.append(card);
    });
  } catch (error) { list.textContent = error.message; }
}

document.querySelector("#load-payments").addEventListener("click", loadPayments);

document.querySelector("#quote-form").addEventListener("submit", async event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const status = document.querySelector("#quote-form-status");
  status.textContent = "Enviando orçamento…";
  try {
    const response = await fetch("api/orcamentos", {method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","X-CSRF-Token":currentUser?.csrf || ""},body:new URLSearchParams({solicitacaoId:form.get("solicitacaoId"),valor:form.get("valor"),prazoDias:form.get("prazoDias"),observacoes:form.get("observacoes")})});
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível enviar orçamento.");
    status.textContent = "Orçamento #" + result.id + " enviado.";
    event.currentTarget.reset();
    await loadQuotes(); await loadOfficeRequests();
  } catch (error) { status.textContent = error.message; }
});

document.querySelector("#vehicle-form").addEventListener("submit", async event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const status = document.querySelector("#vehicle-status");
  status.textContent = "Salvando veículo…";
  try {
    const response = await fetch("api/veiculos", {
      method:"POST",
      headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","X-CSRF-Token":currentUser?.csrf || ""},
      body:new URLSearchParams({marca:form.get("marca"),modelo:form.get("modelo"),ano:form.get("ano"),quilometragem:form.get("quilometragem"),placa:form.get("placa"),motorizacao:form.get("motorizacao")})
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível salvar veículo.");
    event.currentTarget.reset();
    status.textContent = "Veículo #" + result.id + " salvo no banco.";
    await loadVehicles();
  } catch (error) { status.textContent = error.message; }
});

document.querySelector("#request-form").addEventListener("submit", async event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const status = document.querySelector("#request-status");
  status.textContent = "Enviando solicitação…";
  try {
    const response = await fetch("api/solicitacoes", {
      method:"POST",
      headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","X-CSRF-Token":currentUser?.csrf || ""},
      body:new URLSearchParams({veiculoId:form.get("veiculoId"),servicoId:form.get("servicoId"),descricao:form.get("descricao")})
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível enviar solicitação.");
    status.textContent = "Solicitação #" + result.id + " enviada com status " + result.status + ".";
    event.currentTarget.reset();
    await loadRequests();
    await loadQuotes();
    await loadOfficeRequests();
    await loadPayments();
  } catch (error) { status.textContent = error.message; }
});


document.querySelector("#quote-edit-close").addEventListener("click", () => quoteEditDialog.close());
document.querySelector("#quote-edit-form").addEventListener("submit", async event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const status = document.querySelector("#quote-edit-status");
  status.textContent = "Salvando alterações…";
  try {
    const response = await fetch("api/orcamentos/editar", {method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","X-CSRF-Token":currentUser?.csrf || ""},body:new URLSearchParams({id:form.get("id"),valor:form.get("valor"),prazoDias:form.get("prazoDias"),observacoes:form.get("observacoes")})});
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível atualizar orçamento.");
    status.textContent = "Orçamento atualizado.";
    quoteEditDialog.close();
    await loadOrders(); await loadQuotes();
  } catch (error) { status.textContent = error.message; }
});

document.querySelector("#refresh-button").addEventListener("click", loadOrders);
searchInput.addEventListener("input", renderOrders);

document.querySelector("#diagnostic-form").addEventListener("submit", async event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const status = document.querySelector("#diagnostic-status");
  status.textContent = "Enviando relato…";
  try {
    const response = await fetch("api/diagnosticos", {
      method:"POST", headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","X-CSRF-Token":currentUser?.csrf || ""},
      body:new URLSearchParams({vehicle:form.get("vehicle"),symptoms:form.get("symptoms"),priority:form.get("priority")})
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível registrar o relato.");
    status.textContent = "Relato registrado com o identificador " + result.id + ". Ele não equivale a um laudo técnico.";
    event.currentTarget.reset();
  } catch (error) { status.textContent = error.message; }
});

document.querySelector("#checkout-form").addEventListener("submit", async event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const status = document.querySelector("#checkout-status");
  status.textContent = "Criando registro demonstrativo pendente…";
  try {
    const response = await fetch("api/checkout", {
      method:"POST", headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","Idempotency-Key":crypto.randomUUID().replaceAll("-",""),"X-CSRF-Token":currentUser?.csrf || ""},
      body:new URLSearchParams({quoteId:form.get("quoteId"),method:form.get("method")})
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível criar o registro.");
    status.textContent = "Registro de demonstração #" + result.paymentId + " criado com status PENDENTE. Nenhuma cobrança foi realizada.";
    await loadPayments();
  } catch (error) { status.textContent = error.message; }
});

document.querySelector("#auth-open").addEventListener("click", async () => {
  if (!currentUser) { authDialog.showModal(); return; }
  try {
    const response = await fetch("api/auth", {method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8"},body:new URLSearchParams({action:"logout"})});
    if (!response.ok) throw new Error("Não foi possível encerrar a sessão.");
    currentUser = null;
    document.querySelector("#auth-open").textContent = "Entrar";
    applyRoleUI();
    await loadOrders();
  } catch (error) { apiStatus.textContent = error.message; }
});
document.querySelector("#auth-close").addEventListener("click", () => authDialog.close());
document.querySelector("#auth-toggle").addEventListener("click", () => {
  authMode = authMode === "login" ? "register" : "login";
  document.querySelector("#auth-title").textContent = authMode === "login" ? "Entrar" : "Criar conta";
  document.querySelector("#auth-submit").textContent = authMode === "login" ? "Entrar" : "Cadastrar";
  document.querySelector("#auth-name-wrap").hidden = authMode !== "register";
  document.querySelector("#auth-form [name=password]").autocomplete = authMode === "login" ? "current-password" : "new-password";
  document.querySelector("#auth-form [name=password]").minLength = authMode === "login" ? 1 : 10;
  document.querySelector("#auth-toggle").textContent = authMode === "login" ? "Criar uma conta de cliente" : "Já tenho conta? Entrar";
});
document.querySelector("#auth-form").addEventListener("submit", async event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const status = document.querySelector("#auth-status");
  status.textContent = "Processando…";
  try {
    const response = await fetch("api/auth", {method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8"},body:new URLSearchParams({action:authMode,name:form.get("name"),email:form.get("email"),password:form.get("password")})});
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível autenticar.");
    currentUser = result;
    document.querySelector("#auth-open").textContent = result.nome + " · Sair";
    status.textContent = "Autenticado como " + result.perfil + ".";
    authDialog.close();
    applyRoleUI();
    await loadOrders();
    await loadVehicles();
    await loadRequests();
  } catch (error) { status.textContent = error.message; }
});
(async () => {
  try {
    const response = await fetch("api/auth");
    if (response.ok) {
      currentUser = await response.json();
      document.querySelector("#auth-open").textContent = currentUser.nome + " · Sair";
    }
  } catch {}
  applyRoleUI();
  loadOrders();
  loadVehicles();
  loadRequests();
  loadQuotes();
  loadOfficeRequests();
  loadPayments();
})();