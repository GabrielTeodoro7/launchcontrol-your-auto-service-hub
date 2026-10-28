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
  const partsAssistantNote = document.querySelector("#parts-assistant-note");
  if (partsAssistantNote) {
    partsAssistantNote.textContent = role === "CLIENTE"
      ? "Ao enviar uma mensagem, o texto e os dados da peça preenchidos são enviados ao provedor de IA configurado no servidor. Não inclua dados pessoais. A pesquisa manual continua disponível."
      : "Entre como cliente para conversar com a IA. A pesquisa manual de peças continua disponível sem login.";
  }
  document.querySelector("#garagem-inteligente").hidden = role !== "CLIENTE";
  document.querySelector("#office-tools").hidden = role !== "OFICINA";
  document.querySelector("#quote-panel").hidden = !["CLIENTE", "OFICINA", "ADMINISTRADOR"].includes(role);
  document.querySelector("#checkout").hidden = role !== "CLIENTE";
  document.querySelector("#diagnostico").hidden = role !== "CLIENTE";
  document.querySelector("#estoque-inteligente").hidden = !["FORNECEDOR", "ADMINISTRADOR"].includes(role);
  document.querySelector("#inventory-supplier-field").hidden = role !== "ADMINISTRADOR";
  document.querySelectorAll(".nav-item[data-profiles]").forEach(link => {
    const allowed = link.dataset.profiles.split(",");
    link.hidden = !allowed.includes(role);
  });
}

async function loadVehicles() {
  if (!currentUser || currentUser.perfil !== "CLIENTE") return;
  const select = document.querySelector("#vehicle-select");
  try {
    const response = await fetch("api/veiculos");
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível carregar veículos.");
    const partsSelect = document.querySelector("#parts-vehicle-select");
    const garageSelect = document.querySelector("#garage-vehicle-select");
    const garagePlaceholder = document.createElement("option");
    garagePlaceholder.value = "";
    garagePlaceholder.textContent = result.length ? "Selecione um veículo" : "Cadastre um veículo primeiro";
    garageSelect.replaceChildren(garagePlaceholder);
    result.forEach(vehicle => {
      const option = document.createElement("option");
      option.value = vehicle.id;
      option.textContent = vehicle.marca + " " + vehicle.modelo + (vehicle.ano ? " (" + vehicle.ano + ")" : "");
      garageSelect.append(option);
    });
    const partsPlaceholder = document.createElement("option");
    partsPlaceholder.value = "";
    partsPlaceholder.textContent = "Preencher manualmente";
    partsSelect.replaceChildren(partsPlaceholder);
    result.forEach(vehicle => {
      const option = document.createElement("option");
      option.value = vehicle.id;
      option.textContent = vehicle.marca + " " + vehicle.modelo + (vehicle.ano ? " (" + vehicle.ano + ")" : "");
      option.dataset.marca = vehicle.marca || "";
      option.dataset.modelo = vehicle.modelo || "";
      option.dataset.ano = vehicle.ano || "";
      option.dataset.motorizacao = vehicle.motorizacao || "";
      partsSelect.append(option);
    });
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

// Organiza os registros existentes por veículo, sem afirmar que o histórico seja completo.
function formatGarageDate(value) {
  if (!value) return "Data não registrada";
  const parsed = new Date(String(value).replace(" ", "T"));
  if (Number.isNaN(parsed.getTime())) return "Data não disponível";
  return new Intl.DateTimeFormat("pt-BR", {dateStyle:"medium", timeStyle:"short"}).format(parsed);
}

async function loadGarage() {
  if (!currentUser || currentUser.perfil !== "CLIENTE") return;
  const timeline = document.querySelector("#garage-timeline");
  const diagnostics = document.querySelector("#garage-diagnostics");
  const summary = document.querySelector("#garage-vehicle-summary");
  const selectedId = document.querySelector("#garage-vehicle-select").value;
  timeline.replaceChildren();
  diagnostics.replaceChildren();
  summary.replaceChildren();
  try {
    const responses = await Promise.all([
      fetch("api/veiculos"), fetch("api/solicitacoes"), fetch("api/orcamentos"),
      fetch("api/ordens-servico"), fetch("api/diagnosticos")
    ]);
    const labels = ["veículos", "solicitações", "orçamentos", "ordens de serviço", "relatos de diagnóstico"];
    const data = [];
    for (let i = 0; i < responses.length; i++) {
      const result = await responses[i].json();
      if (!responses[i].ok) throw new Error(result.erro || "Não foi possível carregar " + labels[i] + ".");
      data.push(result);
    }
    const [vehicles, requests, quotes, ordersForGarage, diagnoses] = data;
    const vehicle = vehicles.find(item => String(item.id) === selectedId);
    if (vehicle) {
      const title = document.createElement("strong");
      title.textContent = [vehicle.marca, vehicle.modelo, vehicle.ano].filter(Boolean).join(" ");
      const details = document.createElement("p");
      details.textContent = [
        vehicle.motorizacao ? "Motor: " + vehicle.motorizacao : "",
        vehicle.quilometragem != null ? Number(vehicle.quilometragem).toLocaleString("pt-BR") + " km registrados" : "",
        vehicle.placa ? "Placa: " + vehicle.placa : ""
      ].filter(Boolean).join(" · ") || "Sem detalhes adicionais cadastrados.";
      summary.append(title, details);
    }

    const vehicleRequests = selectedId ? requests.filter(item => String(item.veiculoId) === selectedId) : [];
    const requestIds = new Set(vehicleRequests.map(item => String(item.id)));
    const events = [];
    vehicleRequests.forEach(item => events.push({
      type:"Solicitação", date:item.criadaEm, sortDate:item.criadaEm,
      title:"Solicitação #" + item.id, status:item.status,
      description:item.descricao || "Sem descrição registrada."
    }));
    quotes.filter(item => requestIds.has(String(item.solicitacaoId))).forEach(item => events.push({
      type:"Orçamento", date:item.criadoEm, sortDate:item.criadoEm,
      title:"Orçamento #" + item.id, status:item.status,
      description:(item.oficina || "Oficina") + " · " + currency(item.valor) +
        " · Prazo: " + (item.prazoDias == null ? "a combinar" : item.prazoDias + " dia(s)")
    }));
    ordersForGarage.filter(item => requestIds.has(String(item.solicitacaoId))).forEach(item => events.push({
      type:"Ordem de serviço", date:item.criadoEm, sortDate:item.criadoEm,
      title:item.codigo || "Ordem #" + item.id, status:item.status,
      description:(item.servico || "Serviço automotivo") + " · " + currency(item.valor) +
        (item.concluidoEm ? " · Concluída em " + formatGarageDate(item.concluidoEm) : "")
    }));
    events.sort((a,b) => {
      const first = a.sortDate ? new Date(String(a.sortDate).replace(" ", "T")).getTime() : 0;
      const second = b.sortDate ? new Date(String(b.sortDate).replace(" ", "T")).getTime() : 0;
      return second - first;
    });
    if (!selectedId) {
      const empty = document.createElement("p");
      empty.className = "muted";
      empty.textContent = vehicles.length ? "Selecione um veículo para consultar seu histórico." : "Você ainda não cadastrou veículos.";
      timeline.append(empty);
    } else if (!events.length) {
      const empty = document.createElement("p");
      empty.className = "muted";
      empty.textContent = "Não há solicitações, orçamentos ou ordens registrados para este veículo.";
      timeline.append(empty);
    } else {
      events.forEach(event => {
        const card = document.createElement("article");
        card.className = "garage-event";
        const type = document.createElement("span");
        type.className = "eyebrow";
        type.textContent = event.type;
        const title = document.createElement("strong");
        title.textContent = event.title;
        const status = document.createElement("span");
        status.className = "status-badge " + String(event.status || "").toLowerCase();
        status.textContent = event.status || "Sem status";
        const description = document.createElement("p");
        description.textContent = event.description;
        const date = document.createElement("small");
        date.textContent = formatGarageDate(event.date);
        card.append(type, title, status, description, date);
        timeline.append(card);
      });
    }

    if (!diagnoses.length) {
      const empty = document.createElement("p");
      empty.className = "muted";
      empty.textContent = "Nenhum relato de diagnóstico registrado.";
      diagnostics.append(empty);
    } else {
      diagnoses.forEach(item => {
        const card = document.createElement("article");
        card.className = "garage-event";
        const title = document.createElement("strong");
        title.textContent = item.veiculoDescricao || "Veículo não identificado";
        const status = document.createElement("span");
        status.className = "status-badge " + String(item.status || "").toLowerCase();
        status.textContent = item.status || "Sem status";
        const symptoms = document.createElement("p");
        symptoms.textContent = item.sintomas || "Sem sintomas registrados.";
        const priority = document.createElement("small");
        priority.textContent = "Prioridade: " + (item.prioridade || "não informada") + " · " + formatGarageDate(item.criadoEm);
        card.append(title, status, symptoms, priority);
        if (item.hipotesesPreliminares) {
          const hypotheses = document.createElement("p");
          hypotheses.textContent = "Hipóteses preliminares: " + item.hipotesesPreliminares;
          card.append(hypotheses);
        }
        if (item.laudoTecnico) {
          const report = document.createElement("p");
          report.className = "garage-report";
          report.textContent = "Laudo técnico registrado: " + item.laudoTecnico;
          card.append(report);
        } else {
          const pending = document.createElement("small");
          pending.className = "muted";
          pending.textContent = "Ainda não há laudo técnico registrado.";
          card.append(pending);
        }
        diagnostics.append(card);
      });
    }
  } catch (error) {
    const message = document.createElement("p");
    message.className = "form-status";
    message.textContent = error.message || "Não foi possível carregar a garagem.";
    timeline.append(message);
    diagnostics.textContent = "Os relatos não puderam ser carregados nesta tentativa.";
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
    const searchTerm = (document.querySelector("#quote-search")?.value || "").trim().toLocaleLowerCase("pt-BR");
    const sortMode = document.querySelector("#quote-sort")?.value || "recommended";
    const matching = result.filter(quote => [
      quote.oficina, quote.descricao, quote.status, quote.observacoes, String(quote.id), String(quote.solicitacaoId)
    ].some(value => String(value || "").toLocaleLowerCase("pt-BR").includes(searchTerm)));
    const comparablePrices = matching.map(q => Number(q.valor)).filter(Number.isFinite);
    const deadlines = matching.map(q => Number(q.prazoDias)).filter(value => Number.isFinite(value) && value >= 0);
    const summary = document.querySelector("#quote-comparison-summary");
    summary.replaceChildren();
    if (matching.length) {
      const note = document.createElement("p");
      const lowestPrice = comparablePrices.length ? Math.min(...comparablePrices) : null;
      const shortest = deadlines.length ? Math.min(...deadlines) : null;
      note.textContent = matching.length + (matching.length === 1 ? " proposta encontrada" : " propostas encontradas") +
        (lowestPrice == null ? ". Não há preços comparáveis." : ". Menor valor informado: " + currency(lowestPrice) + ".") +
        (shortest == null ? " Prazo não informado nas propostas." : " Menor prazo informado: " + shortest + (shortest === 1 ? " dia." : " dias."));
      summary.append(note);
    }
    matching.sort((a,b) => {
      if (sortMode === "price") return Number(a.valor) - Number(b.valor);
      if (sortMode === "deadline") return (a.prazoDias == null ? Infinity : Number(a.prazoDias)) - (b.prazoDias == null ? Infinity : Number(b.prazoDias));
      if (sortMode === "newest") return new Date(b.criadoEm || 0) - new Date(a.criadoEm || 0);
      return Number(a.valor) - Number(b.valor) || (a.prazoDias == null ? Infinity : Number(a.prazoDias)) - (b.prazoDias == null ? Infinity : Number(b.prazoDias));
    });
    if (!matching.length) {
      const empty = document.createElement("p"); empty.className = "muted";
      empty.textContent = result.length ? "Nenhuma proposta corresponde à pesquisa." : "Nenhum orçamento encontrado para esta conta.";
      list.replaceChildren(empty); return;
    }
    list.replaceChildren();
    matching.forEach(quote => {
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
              await loadQuotes(); await loadOrders(); await loadGarage();
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

async function loadInventory() {
  if (!currentUser || !["FORNECEDOR", "ADMINISTRADOR"].includes(currentUser.perfil)) return;
  const list = document.querySelector("#inventory-list");
  const summary = document.querySelector("#inventory-summary");
  list.replaceChildren();
  summary.replaceChildren();
  const query = new URLSearchParams({
    q: document.querySelector("#inventory-search").value.trim(),
    estoque: document.querySelector("#inventory-filter").value
  });
  try {
    const response = await fetch("api/estoque?" + query.toString(), {headers:{"Accept":"application/json"}});
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível carregar o estoque.");
    const low = result.filter(item => item.estoque > 0 && item.estoque <= 5).length;
    const zero = result.filter(item => item.estoque === 0).length;
    const total = result.length;
    const metrics = [
      ["Itens listados", total],
      ["Estoque baixo", low],
      ["Sem unidades", zero]
    ];
    metrics.forEach(([label, value]) => {
      const card = document.createElement("div"); card.className = "inventory-metric";
      const name = document.createElement("span"); name.textContent = label;
      const number = document.createElement("strong"); number.textContent = value;
      card.append(name, number); summary.append(card);
    });
    if (!result.length) {
      const empty = document.createElement("p"); empty.className = "muted";
      empty.textContent = "Nenhuma peça encontrada para estes filtros. Cadastre uma peça ou ajuste a pesquisa.";
      list.append(empty); return;
    }
    result.forEach(item => {
      const card = document.createElement("article"); card.className = "inventory-item";
      const top = document.createElement("div"); top.className = "inventory-item-top";
      const title = document.createElement("strong"); title.textContent = item.nome;
      const badge = document.createElement("span"); badge.className = "status-badge " + (item.situacao === "ZERADO" ? "aguardando_peca" : item.situacao === "BAIXO" ? "pendente" : "concluido"); badge.textContent = item.situacao === "ZERADO" ? "Sem estoque" : item.situacao === "BAIXO" ? "Estoque baixo" : "Disponível";
      top.append(title, badge);
      const details = document.createElement("p"); details.textContent = [item.marca, item.codigo ? "Código: " + item.codigo : "", "Quantidade: " + item.estoque, "Preço: " + currency(item.preco), item.ativo ? "Cadastro ativo" : "Cadastro inativo"].filter(Boolean).join(" · ");
      const edit = document.createElement("button"); edit.type = "button"; edit.className = "mini-button"; edit.textContent = "Editar item";
      edit.addEventListener("click", () => {
        const form = document.querySelector("#inventory-form");
        ["id","nome","marca","codigo","descricao","preco","estoque","fornecedorId"].forEach(key => { form.elements[key].value = item[key] ?? ""; });
        form.elements.ativo.value = String(item.ativo);
        document.querySelector("#inventory-form-title").textContent = "Editar peça #" + item.id;
        document.querySelector("#inventory-status").textContent = "Atualize os dados e salve para persistir as alterações.";
        form.scrollIntoView({behavior:"smooth",block:"center"});
      });
      card.append(top, details, edit); list.append(card);
    });
  } catch (error) {
    const message = document.createElement("p"); message.className = "form-status"; message.textContent = error.message || "Falha ao consultar estoque."; list.append(message);
  }
}

document.querySelector("#inventory-search").addEventListener("input", () => loadInventory());
document.querySelector("#inventory-filter").addEventListener("change", () => loadInventory());
document.querySelector("#inventory-refresh").addEventListener("click", loadInventory);
document.querySelector("#inventory-cancel-edit").addEventListener("click", () => {
  document.querySelector("#inventory-form").reset();
  document.querySelector("#inventory-form").elements.id.value = "";
  document.querySelector("#inventory-form-title").textContent = "Cadastrar peça no estoque";
});
document.querySelector("#inventory-form").addEventListener("submit", async event => {
  event.preventDefault();
  const form = event.currentTarget;
  const values = new FormData(form);
  const status = document.querySelector("#inventory-status");
  status.textContent = "Salvando no estoque…";
  const payload = {};
  ["id","nome","marca","codigo","descricao","preco","estoque","ativo","fornecedorId"].forEach(key => payload[key] = values.get(key));
  try {
    const response = await fetch("api/estoque", {
      method:"POST",
      headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","X-CSRF-Token":currentUser?.csrf || ""},
      body:new URLSearchParams(payload)
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível salvar a peça.");
    status.textContent = "Peça #" + result.id + " salva no estoque.";
    form.reset(); form.elements.id.value = "";
    document.querySelector("#inventory-form-title").textContent = "Cadastrar peça no estoque";
    await loadInventory();
  } catch (error) { status.textContent = error.message; }
});

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
document.querySelector("#quote-search").addEventListener("input", () => loadQuotes());
document.querySelector("#quote-sort").addEventListener("change", () => loadQuotes());

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
    await loadGarage();
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
    await loadGarage();
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

// Reutiliza um veículo salvo quando o cliente escolhe um; não altera os dados do cadastro.
document.querySelector("#parts-vehicle-select").addEventListener("change", event => {
  const option = event.currentTarget.selectedOptions[0];
  if (!option?.value) return;
  const form = document.querySelector("#parts-search-form");
  form.elements.marca.value = option.dataset.marca || "";
  form.elements.modelo.value = option.dataset.modelo || "";
  form.elements.ano.value = option.dataset.ano || "";
  form.elements.motorizacao.value = option.dataset.motorizacao || "";
});

// Monta links para as lojas. Esses links abrem a plataforma de origem, sem intermediar a compra.
function renderExternalPartSources(query, container) {
  const encoded = encodeURIComponent(query);
  const slug = query.trim().split(/\s+/).map(encodeURIComponent).join("-");
  const sources = [
    {name:"Mercado Livre",detail:"Anúncios consultados pela integração quando disponível",url:"https://lista.mercadolivre.com.br/" + slug},
    {name:"Shopee",detail:"Resultados abertos na plataforma original",url:"https://shopee.com.br/search?keyword=" + encoded},
    {name:"Amazon Brasil",detail:"Pesquisar peças e acessórios automotivos",url:"https://www.amazon.com.br/s?k=" + encodeURIComponent(query + " autopeças")},
    {name:"OLX",detail:"Anúncios novos e usados publicados na OLX",url:"https://www.olx.com.br/brasil?q=" + encoded},
    {name:"Connect Parts",detail:"Busca em varejista especializado; resultados devem ser conferidos",url:"https://www.google.com/search?q=" + encodeURIComponent("site:connectparts.com.br " + query)},
    {name:"Jocar",detail:"Pesquisar catálogo de peças automotivas",url:"https://www.google.com/search?q=" + encodeURIComponent("site:jocar.com.br " + query)},
    {name:"AutoZ",detail:"Pesquisar peças e acessórios na loja",url:"https://www.google.com/search?q=" + encodeURIComponent("AutoZ autopeças " + query)},
    {name:"Lojas oficiais",detail:"Busca na web; confirme se o vendedor é realmente oficial",url:"https://www.google.com/search?q=" + encodeURIComponent(query + " autopeças loja oficial fabricante")}
  ];
  const heading = document.createElement("h3");
  heading.className = "parts-external-heading";
  heading.textContent = "Pesquisar também em outras plataformas";
  container.append(heading);
  const list = document.createElement("div");
  list.className = "parts-source-list";
  sources.forEach(source => {
    const item = document.createElement("article");
    item.className = "parts-source";
    const title = document.createElement("strong");
    title.textContent = source.name;
    const detail = document.createElement("p");
    detail.textContent = source.detail;
    const link = document.createElement("a");
    link.className = "button button-outline";
    link.href = source.url;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    link.textContent = "Abrir pesquisa";
    item.append(title, detail, link);
    list.append(item);
  });
  container.append(list);
}

function validMercadoLivreUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" &&
      (url.hostname === "mercadolivre.com.br" || url.hostname.endsWith(".mercadolivre.com.br") ||
       url.hostname === "mercadolibre.com" || url.hostname.endsWith(".mercadolibre.com"));
  } catch {
    return false;
  }
}

async function searchParts(query) {
  const results = document.querySelector("#parts-results");
  const status = document.querySelector("#parts-search-status");
  results.replaceChildren();
  results.hidden = false;
  const heading = document.createElement("h3");
  heading.textContent = "Resultados de pesquisa";
  results.append(heading);
  const querySummary = document.createElement("p");
  querySummary.className = "muted";
  querySummary.textContent = "Pesquisa: " + query;
  results.append(querySummary);
  const loading = document.createElement("p");
  loading.className = "parts-live-note";
  loading.textContent = "Consultando anúncios públicos do Mercado Livre…";
  results.append(loading);
  status.textContent = "Consultando o Mercado Livre e preparando links para outras lojas.";
  let liveSearchSucceeded = false;
  try {
    const response = await fetch("api/pecas/busca?q=" + encodeURIComponent(query));
    const data = await response.json();
    if (!response.ok) throw new Error(data.erro || "Não foi possível consultar o Mercado Livre agora.");
    loading.remove();
    liveSearchSucceeded = true;
    const items = Array.isArray(data.results) ? data.results : [];
    const note = document.createElement("p");
    note.className = "parts-live-note";
    note.textContent = "Fonte consultada: API pública do Mercado Livre. O valor exibido é o preço anunciado; o frete pode não estar incluído. A compatibilidade com seu veículo não foi confirmada pelo LaunchControl.";
    results.append(note);
    if (!items.length) {
      const empty = document.createElement("p");
      empty.className = "muted";
      empty.textContent = "A API não retornou anúncios para esta consulta. Você ainda pode abrir a pesquisa diretamente na plataforma.";
      results.append(empty);
    } else {
      const liveList = document.createElement("div");
      liveList.className = "parts-live-list";
      items.forEach(item => {
        if (!item || !validMercadoLivreUrl(item.permalink)) return;
        const card = document.createElement("article");
        card.className = "parts-live-item";
        if (item.thumbnail && /^https:\/\//i.test(item.thumbnail)) {
          const image = document.createElement("img");
          image.src = item.thumbnail;
          image.alt = item.title ? "Imagem do anúncio: " + item.title : "Imagem do produto";
          image.loading = "lazy";
          image.referrerPolicy = "no-referrer";
          image.addEventListener("error", () => image.remove(), {once:true});
          card.append(image);
        }
        const title = document.createElement("strong");
        title.textContent = item.title || "Anúncio sem título";
        const price = document.createElement("span");
        price.className = "parts-live-price";
        if (typeof item.price === "number" && Number.isFinite(item.price)) {
          const currencyCode = /^[A-Z]{3}$/.test(item.currency_id || "") ? item.currency_id : "BRL";
          try {
            price.textContent = new Intl.NumberFormat("pt-BR", {style:"currency",currency:currencyCode}).format(item.price);
          } catch {
            price.textContent = item.price.toLocaleString("pt-BR") + " " + currencyCode;
          }
        } else {
          price.textContent = "Preço não informado";
        }
        const seller = document.createElement("p");
        seller.textContent = item.seller && item.seller.nickname ? "Vendedor: " + item.seller.nickname : "Vendedor: consulte o anúncio";
        const condition = document.createElement("p");
        condition.textContent = item.condition === "new" ? "Condição informada: novo" :
          item.condition === "used" ? "Condição informada: usado" : "Condição: confira no anúncio";
        const shipping = document.createElement("p");
        shipping.textContent = item.shipping && item.shipping.free_shipping === true ?
          "O anúncio informa frete grátis; confirme o CEP e as condições." : "Frete não confirmado nesta comparação.";
        const link = document.createElement("a");
        link.className = "button button-outline";
        link.href = item.permalink;
        link.target = "_blank";
        link.rel = "noopener noreferrer";
        link.textContent = "Ver anúncio no Mercado Livre";
        card.append(title, price, seller, condition, shipping, link);
        liveList.append(card);
      });
      if (liveList.childElementCount) results.append(liveList);
      else {
        const empty = document.createElement("p");
        empty.className = "muted";
        empty.textContent = "A API retornou resultados, mas nenhum link passou pela validação de segurança.";
        results.append(empty);
      }
    }
  } catch (error) {
    loading.textContent = error.message || "A consulta em tempo real falhou.";
    loading.className = "parts-live-note";
    if (!loading.isConnected) results.append(loading);
  }
  renderExternalPartSources(query, results);
  status.textContent = liveSearchSucceeded
    ? "Pesquisa concluída. Confira o anúncio original, o frete, a reputação do vendedor e a aplicação exata antes de comprar."
    : "A consulta em tempo real não ficou disponível nesta tentativa; os links externos continuam disponíveis.";
  results.scrollIntoView({behavior:"smooth", block:"nearest"});
}

document.querySelector("#parts-search-form").addEventListener("submit", event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const values = [
    form.get("marca"), form.get("modelo"), form.get("ano"), form.get("versao"),
    form.get("motorizacao"), form.get("peca"), form.get("oem"),
    form.get("cor") ? "cor " + form.get("cor") : "",
    form.get("condicao")
  ].map(value => String(value || "").trim()).filter(Boolean);
  const query = values.join(" ");
  if (query.length < 3) {
    document.querySelector("#parts-search-status").textContent = "Informe o veículo e a peça para montar a pesquisa.";
    return;
  }
  searchParts(query);
});

const partsAssistantMessages = document.querySelector("#parts-assistant-messages");
const partsAssistantStatus = document.querySelector("#parts-assistant-status");
const partsAssistantHistory = [];

function addPartsAssistantMessage(role, text) {
  const message = document.createElement("div");
  message.className = "assistant-message " + role;
  message.textContent = text;
  partsAssistantMessages.append(message);
  partsAssistantMessages.scrollTop = partsAssistantMessages.scrollHeight;
}

function assistantField(answer, field) {
  const prefix = field + ":";
  const line = answer.split(/\r?\n/).find(value => value.trim().toLocaleUpperCase("pt-BR").startsWith(prefix));
  return line ? line.trim().slice(prefix.length).trim() : "";
}

document.querySelector("#parts-assistant-form").addEventListener("submit", async event => {
  event.preventDefault();
  if (!currentUser || currentUser.perfil !== "CLIENTE") {
    partsAssistantStatus.textContent = "Entre com uma conta de cliente para usar o assistente IA.";
    if (!authDialog.open) authDialog.showModal();
    return;
  }
  const form = event.currentTarget;
  const message = form.elements.message.value.trim();
  if (!message) return;
  const button = form.querySelector('button[type="submit"]');
  const partsForm = document.querySelector("#parts-search-form");
  const parts = new FormData(partsForm);
  const mode = form.elements.mode.value === "sintomas" ? "sintomas" : "pecas";
  const history = partsAssistantHistory.slice(-6).map(item => item.role + ": " + item.content).join("\n").slice(-2500);
  addPartsAssistantMessage("user", message);
  partsAssistantStatus.textContent = "Analisando a descrição e preparando uma busca segura…";
  button.disabled = true;
  try {
    const body = new URLSearchParams({
      mensagem: message,
      modo: mode,
      historico: history,
      marca: String(parts.get("marca") || ""),
      modelo: String(parts.get("modelo") || ""),
      ano: String(parts.get("ano") || ""),
      versao: String(parts.get("versao") || ""),
      motorizacao: String(parts.get("motorizacao") || ""),
      peca: String(parts.get("peca") || ""),
      oem: String(parts.get("oem") || ""),
      cor: String(parts.get("cor") || ""),
      condicao: String(parts.get("condicao") || "")
    });
    const response = await fetch("api/assistente-pecas", {
      method:"POST",
      headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","X-CSRF-Token":currentUser.csrf || ""},
      body:body.toString()
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível consultar o assistente.");
    const answer = String(result.resposta || "");
    const orientation = assistantField(answer, "ORIENTACAO");
    const query = assistantField(answer, "CONSULTA");
    const missing = assistantField(answer, "FALTA");
    const alert = assistantField(answer, "ALERTA");
    const safetySignal = result.alertaSeguranca === true;
    const visibleAnswer = [
      orientation || answer,
      missing && missing.toUpperCase() !== "VAZIO" ? "Para continuar, preciso de: " + missing : "",
      mode === "sintomas" && (safetySignal || alert.toUpperCase() === "URGENTE")
        ? "ALERTA DE SEGURANÇA: não continue dirigindo se houver risco. Pare em local seguro, desligue o motor se for seguro e procure assistência profissional ou guincho." : ""
    ].filter(Boolean).join("\n\n");
    addPartsAssistantMessage("assistant", visibleAnswer || "Não consegui montar uma orientação. Tente explicar a peça e o veículo com outras palavras.");
    partsAssistantHistory.push({role:"user",content:message},{role:"assistente",content:visibleAnswer || answer});
    if (partsAssistantHistory.length > 12) partsAssistantHistory.splice(0, partsAssistantHistory.length - 12);
    if (mode === "sintomas") {
      partsAssistantStatus.textContent = result.fallback
        ? (result.aviso || "Modo guiado sem IA; não é um diagnóstico técnico.")
        : "Orientação preliminar. Não é um diagnóstico técnico; registre o relato ou procure uma oficina quando necessário.";
    } else if (query && query.toUpperCase() !== "VAZIO" && query.length >= 3) {
      partsAssistantStatus.textContent = "A orientação está pronta; buscando anúncios reais no Mercado Livre.";
      await searchParts(query);
    } else {
      partsAssistantStatus.textContent = "Responda à pergunta do assistente para refinar a pesquisa.";
    }
    form.reset();
  } catch (error) {
    const messageText = error.message || "O assistente não respondeu. A pesquisa manual continua disponível.";
    addPartsAssistantMessage("assistant", messageText);
    partsAssistantStatus.textContent = messageText;
  } finally {
    button.disabled = false;
  }
});

document.querySelector("#garage-vehicle-select").addEventListener("change", async () => { await loadGarage(); await loadMaintenance(); });
document.querySelector("#refresh-button").addEventListener("click", async () => { await loadOrders(); await loadGarage(); });
searchInput.addEventListener("input", renderOrders);

// Registra o relato informado pelo cliente; isso não é um diagnóstico técnico confirmado.
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

// Cria um registro de pagamento demonstrativo, sem efetuar uma cobrança real.
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
    const response = await fetch("api/auth", {method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","X-CSRF-Token":currentUser?.csrf || ""},body:new URLSearchParams({action:"logout"})});
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
    await loadGarage();
    await loadQuotes();
    await loadOfficeRequests();
    await loadInventory();
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
  await loadVehicles();
  await loadRequests();
  await loadGarage();
  loadQuotes();
  loadOfficeRequests();
  loadInventory();
  loadPayments();
})();

// CarPassport: histórico de manutenção persistido pelo endpoint Java existente na branch.
function formatMaintenanceDate(value) {
  if (!value) return "Não informado";
  const parsed = new Date(String(value).slice(0, 10) + "T12:00:00");
  return Number.isNaN(parsed.getTime())
    ? "Data indisponível"
    : new Intl.DateTimeFormat("pt-BR", {dateStyle:"medium"}).format(parsed);
}

async function loadMaintenance() {
  const vehicleId = document.querySelector("#garage-vehicle-select").value;
  const timeline = document.querySelector("#maintenance-timeline");
  const summary = document.querySelector("#maintenance-summary");
  const status = document.querySelector("#maintenance-status");
  timeline.replaceChildren();
  summary.replaceChildren();

  if (!currentUser || currentUser.perfil !== "CLIENTE") {
    timeline.textContent = "Entre como cliente para consultar o passaporte digital.";
    return;
  }
  if (!vehicleId) {
    timeline.textContent = "Selecione um veículo para consultar manutenções.";
    status.textContent = "Selecione um veículo para começar.";
    return;
  }

  timeline.textContent = "Consultando histórico de manutenção…";
  try {
    const response = await fetch("api/manutencoes?veiculoId=" + encodeURIComponent(vehicleId), {
      headers: {"Accept":"application/json"}
    });
    const records = await response.json();
    if (!response.ok) throw new Error(records.erro || "Não foi possível carregar o histórico.");
    const total = records.reduce((sum, item) => sum + Number(item.custo || 0), 0);
    const metrics = [
      ["Registros", String(records.length)],
      ["Custo registrado", currency(total)],
      ["Origem", "Dados cadastrados"]
    ];
    metrics.forEach(([label, value]) => {
      const item = document.createElement("div");
      item.className = "garage-summary-item";
      const title = document.createElement("span");
      title.textContent = label;
      const valueNode = document.createElement("strong");
      valueNode.textContent = value;
      item.append(title, valueNode);
      summary.append(item);
    });

    if (!records.length) {
      timeline.textContent = "Nenhuma manutenção cadastrada para este veículo. Registre o primeiro serviço ao lado.";
    } else {
      records.forEach(record => {
        const item = document.createElement("article");
        item.className = "garage-timeline-item";
        const title = document.createElement("strong");
        title.textContent = record.categoria + " · " + formatMaintenanceDate(record.dataServico);
        const description = document.createElement("p");
        description.textContent = record.descricao;
        const detail = document.createElement("p");
        detail.textContent = [
          record.quilometragem != null ? Number(record.quilometragem).toLocaleString("pt-BR") + " km" : "",
          "Custo: " + currency(record.custo),
          record.oficina ? "Oficina informada: " + record.oficina : "",
          record.proximaData ? "Próxima data: " + formatMaintenanceDate(record.proximaData) : "",
          record.proximaQuilometragem != null ? "Próxima quilometragem: " + Number(record.proximaQuilometragem).toLocaleString("pt-BR") + " km" : ""
        ].filter(Boolean).join(" · ");
        const provenance = document.createElement("small");
        provenance.textContent = "Registro informado pelo proprietário; não equivale a laudo ou comprovante verificado.";
        item.append(title, description, detail, provenance);
        timeline.append(item);
      });
    }
    status.textContent = records.length + (records.length === 1 ? " manutenção registrada." : " manutenções registradas.");
  } catch (error) {
    timeline.textContent = error.message || "Não foi possível carregar o histórico.";
    status.textContent = error.message || "Falha ao consultar o passaporte digital.";
  }
}

document.querySelector("#maintenance-form").addEventListener("submit", async event => {
  event.preventDefault();
  const status = document.querySelector("#maintenance-status");
  const vehicleId = document.querySelector("#garage-vehicle-select").value;
  if (!currentUser || currentUser.perfil !== "CLIENTE") {
    status.textContent = "Entre como cliente para registrar uma manutenção.";
    return;
  }
  if (!vehicleId) {
    status.textContent = "Selecione um veículo antes de salvar.";
    return;
  }
  const form = new FormData(event.currentTarget);
  status.textContent = "Salvando manutenção…";
  const body = new URLSearchParams({
    veiculoId: vehicleId,
    categoria: form.get("categoria"),
    descricao: form.get("descricao"),
    dataServico: form.get("dataServico"),
    quilometragem: form.get("quilometragem"),
    custo: form.get("custo"),
    oficina: form.get("oficina"),
    proximaData: form.get("proximaData"),
    proximaQuilometragem: form.get("proximaQuilometragem")
  });
  try {
    const response = await fetch("api/manutencoes", {
      method: "POST",
      headers: {
        "Content-Type":"application/x-www-form-urlencoded;charset=UTF-8",
        "X-CSRF-Token":currentUser.csrf || ""
      },
      body
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "Não foi possível salvar a manutenção.");
    event.currentTarget.reset();
    status.textContent = "Manutenção registrada. Atualizando o histórico…";
    await loadMaintenance();
  } catch (error) {
    status.textContent = error.message || "Falha ao salvar a manutenção.";
  }
});
