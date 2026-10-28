const rows = document.querySelector("#service-rows");
const serviceStatus = document.querySelector("#service-status");
const apiStatus = document.querySelector("#api-status");
const searchInput = document.querySelector("#service-search");
const dialog = document.querySelector("#detail-dialog");
const detailsTitle = document.querySelector("#detail-title");
const detailsContent = document.querySelector("#detail-content");
const authDialog = document.querySelector("#auth-dialog");
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
    if (!completed) {
      const edit = document.createElement("button"); edit.type = "button"; edit.className = "mini-button"; edit.textContent = "Editar orçamento";
      edit.title = "A edição depende de autenticação e permissão da oficina.";
      edit.addEventListener("click", () => {
        alert("A edição só pode ser realizada pela API autenticada da oficina; o formulário de edição ainda não foi liberado nesta etapa.");
      });
      group.append(edit);
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
  } catch (error) { status.textContent = error.message; }
});

document.querySelector("#auth-open").addEventListener("click", async () => {
  if (!currentUser) { authDialog.showModal(); return; }
  try {
    const response = await fetch("api/auth", {method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8"},body:new URLSearchParams({action:"logout"})});
    if (!response.ok) throw new Error("Não foi possível encerrar a sessão.");
    currentUser = null;
    document.querySelector("#auth-open").textContent = "Entrar";
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
    await loadOrders();
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
  loadOrders();
})();