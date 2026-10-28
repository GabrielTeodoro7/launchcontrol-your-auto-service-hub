(() => {
  const money = value => Number(value || 0).toLocaleString("pt-BR", {style:"currency",currency:"BRL"});
  const dateLabel = value => {
    if (!value) return "Data não registrada";
    const date = new Date(String(value).replace(" ", "T"));
    return Number.isNaN(date.getTime()) ? "Data não disponível" : date.toLocaleDateString("pt-BR");
  };
  const escapeHtml = value => String(value ?? "").replace(/[&<>"']/g, char => ({
    "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"
  })[char]);

  function buildPanel() {
    if (document.querySelector("#supplemental-tools")) return;
    const panel = document.createElement("section");
    panel.id = "supplemental-tools";
    panel.className = "panel supplemental-tools";
    panel.hidden = true;
    panel.innerHTML = `
      <div class="panel-heading">
        <div><p class="eyebrow">ACOMPANHAMENTO</p><h2>Serviços adicionais e notificações</h2></div>
        <button type="button" class="button button-secondary" id="supplemental-refresh">Atualizar</button>
      </div>
      <p class="muted">Serviços extras só são autorizados após decisão explícita do cliente. As notificações são internas ao LaunchControl.</p>
      <form id="additional-service-form" class="stack-form" hidden>
        <h3>Propor serviço adicional</h3>
        <label>Ordem de serviço<select name="ordemId" id="additional-order" required><option value="">Carregando ordens…</option></select></label>
        <label>Descrição do serviço<textarea name="descricao" maxlength="500" rows="3" required></textarea></label>
        <label>Valor adicional (R$)<input name="valor" type="number" min="0" max="99999999.99" step="0.01" required></label>
        <button class="button button-primary" type="submit">Enviar para aprovação</button>
        <p class="form-status" id="additional-service-status" role="status"></p>
      </form>
      <div class="supplemental-section"><h3>Serviços adicionais</h3><div id="additional-service-list" class="supplemental-list"><p class="muted">Entre para consultar.</p></div></div>
      <div class="supplemental-section"><div class="panel-heading"><h3>Notificações</h3><span class="muted" id="notification-count"></span></div><div id="notification-list" class="supplemental-list"><p class="muted">Entre para consultar.</p></div></div>
      <div class="supplemental-section"><h3>Passaporte do veículo</h3>
        <p class="muted">Gera uma versão para impressão do histórico de manutenção registrado. Na janela de impressão, escolha “Salvar como PDF”.</p>
        <button type="button" class="button button-secondary" id="maintenance-export">Exportar histórico para PDF</button>
        <p class="form-status" id="maintenance-export-status" role="status"></p>
        <form id="maintenance-attachment-form" class="stack-form">
          <h3>Anexar comprovante ou foto</h3>
          <label>Veículo<select id="attachment-vehicle" required><option value="">Carregando veículos…</option></select></label>
          <label>Registro de manutenção<select id="attachment-history" name="historicoId" required><option value="">Selecione um veículo primeiro</option></select></label>
          <label>Arquivo (PDF, PNG ou JPEG; até 5 MB)<input type="file" name="arquivo" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" required></label>
          <button class="button button-primary" type="submit">Enviar anexo</button>
          <p class="form-status" id="maintenance-attachment-status" role="status"></p>
        </form>
        <div class="supplemental-section"><h3>Anexos do veículo</h3><div id="maintenance-attachment-list" class="supplemental-list"><p class="muted">Selecione um veículo para consultar.</p></div></div>
      </div>`;
    const main = document.querySelector("main") || document.body;
    main.append(panel);
    panel.querySelector("#supplemental-refresh").addEventListener("click", refreshSupplementalFeatures);
    panel.querySelector("#additional-service-form").addEventListener("submit", submitAdditionalService);
    panel.querySelector("#maintenance-export").addEventListener("click", exportMaintenanceHistory);
    panel.querySelector("#maintenance-attachment-form").addEventListener("submit", submitMaintenanceAttachment);
    panel.querySelector("#attachment-vehicle").addEventListener("change", loadMaintenanceAttachmentHistory);
  }

  async function fetchJson(url, options) {
    const response = await fetch(url, options);
    const result = await response.json();
    if (!response.ok) throw new Error(result.erro || "A solicitação não pôde ser concluída.");
    return result;
  }

  async function loadOrdersForProposal() {
    const select = document.querySelector("#additional-order");
    if (!select) return;
    const orders = await fetchJson("api/ordens-servico", {headers:{"Accept":"application/json"}});
    select.replaceChildren(new Option("Selecione uma ordem", ""));
    orders.filter(order => order.status !== "CONCLUIDO").forEach(order => {
      const option = new Option(
        (order.codigo || "Ordem #" + order.id) + " · " + (order.cliente || "Cliente") + " · " + (order.veiculo || "Veículo"),
        order.id
      );
      select.append(option);
    });
  }

  async function loadAdditionalServices() {
    const list = document.querySelector("#additional-service-list");
    try {
      const items = await fetchJson("api/servicos-adicionais");
      list.replaceChildren();
      if (!items.length) {
        const empty = document.createElement("p");
        empty.className = "muted";
        empty.textContent = "Nenhum serviço adicional registrado.";
        list.append(empty);
        return;
      }
      items.forEach(item => {
        const card = document.createElement("article");
        card.className = "supplemental-item";
        const heading = document.createElement("strong");
        heading.textContent = (item.codigoOrdem || "Ordem #" + item.ordemId) + " · " + money(item.valor);
        const description = document.createElement("p");
        description.textContent = item.descricao;
        const status = document.createElement("small");
        status.textContent = "Status: " + ({PENDENTE:"Aguardando sua decisão",ACEITO:"Aceito",RECUSADO:"Recusado"}[item.status] || item.status);
        card.append(heading, description, status);
        if (currentUser?.perfil === "CLIENTE" && item.status === "PENDENTE") {
          const actions = document.createElement("div");
          actions.className = "supplemental-actions";
          for (const [decision, label] of [["ACEITAR","Aceitar serviço"],["RECUSAR","Recusar serviço"]]) {
            const button = document.createElement("button");
            button.type = "button";
            button.className = decision === "ACEITAR" ? "button button-primary" : "button button-secondary";
            button.textContent = label;
            button.addEventListener("click", async () => {
              button.disabled = true;
              try {
                await fetchJson("api/servicos-adicionais", {
                  method:"POST",
                  headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","X-CSRF-Token":currentUser?.csrf || ""},
                  body:new URLSearchParams({acao:"decidir",id:item.id,decisao:decision})
                });
                await refreshSupplementalFeatures();
              } catch (error) {
                alert(error.message);
                button.disabled = false;
              }
            });
            actions.append(button);
          }
          card.append(actions);
        }
        list.append(card);
      });
    } catch (error) {
      list.textContent = error.message;
    }
  }

  async function loadNotifications() {
    const list = document.querySelector("#notification-list");
    const count = document.querySelector("#notification-count");
    const items = await fetchJson("api/notificacoes");
    list.replaceChildren();
    const unread = items.filter(item => !item.lidaEm).length;
    count.textContent = unread + (unread === 1 ? " não lida" : " não lidas");
    if (!items.length) {
      const empty = document.createElement("p");
      empty.className = "muted";
      empty.textContent = "Você não tem notificações.";
      list.append(empty);
      return;
    }
    items.forEach(item => {
      const card = document.createElement("article");
      card.className = "supplemental-item" + (item.lidaEm ? "" : " is-unread");
      const title = document.createElement("strong");
      title.textContent = item.titulo;
      const message = document.createElement("p");
      message.textContent = item.mensagem;
      const date = document.createElement("small");
      date.textContent = dateLabel(item.criadaEm) + (item.lidaEm ? " · Lida" : " · Não lida");
      card.append(title, message, date);
      if (!item.lidaEm) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "mini-button";
        button.textContent = "Marcar como lida";
        button.addEventListener("click", async () => {
          button.disabled = true;
          try {
            await fetchJson("api/notificacoes", {
              method:"POST",
              headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","X-CSRF-Token":currentUser?.csrf || ""},
              body:new URLSearchParams({id:item.id})
            });
            await loadNotifications();
          } catch (error) { alert(error.message); button.disabled = false; }
        });
        card.append(button);
      }
      list.append(card);
    });
  }

  async function submitAdditionalService(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const status = document.querySelector("#additional-service-status");
    const values = new FormData(form);
    status.textContent = "Enviando proposta para o cliente…";
    try {
      await fetchJson("api/servicos-adicionais", {
        method:"POST",
        headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","X-CSRF-Token":currentUser?.csrf || ""},
        body:new URLSearchParams({acao:"propor",ordemId:values.get("ordemId"),descricao:values.get("descricao"),valor:values.get("valor")})
      });
      status.textContent = "Proposta registrada. A execução deve aguardar a decisão do cliente.";
      form.reset();
      await refreshSupplementalFeatures();
    } catch (error) {
      status.textContent = error.message;
    }
  }

  async function loadMaintenanceAttachmentVehicles() {
    const select = document.querySelector("#attachment-vehicle");
    const vehicles = await fetchJson("api/veiculos");
    const previous = select.value;
    select.replaceChildren(new Option("Selecione um veículo", ""));
    vehicles.forEach(vehicle => select.append(new Option(
      [vehicle.marca, vehicle.modelo, vehicle.ano].filter(Boolean).join(" "),
      vehicle.id
    )));
    if (vehicles.some(vehicle => String(vehicle.id) === previous)) select.value = previous;
    if (select.value) await loadMaintenanceAttachmentHistory();
    else {
      document.querySelector("#attachment-history").replaceChildren(new Option("Selecione um veículo primeiro", ""));
      document.querySelector("#maintenance-attachment-list").textContent = "Selecione um veículo para consultar.";
    }
  }

  async function loadMaintenanceAttachmentHistory() {
    const vehicleId = document.querySelector("#attachment-vehicle").value;
    const historySelect = document.querySelector("#attachment-history");
    const list = document.querySelector("#maintenance-attachment-list");
    historySelect.replaceChildren(new Option(vehicleId ? "Selecione um registro" : "Selecione um veículo primeiro", ""));
    list.replaceChildren();
    if (!vehicleId) return;
    try {
      const [records, attachments] = await Promise.all([
        fetchJson("api/manutencoes?veiculoId=" + encodeURIComponent(vehicleId)),
        fetchJson("api/anexos-manutencao?veiculoId=" + encodeURIComponent(vehicleId))
      ]);
      records.forEach(record => historySelect.append(new Option(
        dateLabel(record.dataServico) + " · " + record.categoria + " · " + record.descricao,
        record.id
      )));
      if (!attachments.length) {
        const empty = document.createElement("p");
        empty.className = "muted";
        empty.textContent = "Nenhum anexo registrado para este veículo.";
        list.append(empty);
      } else {
        attachments.forEach(item => {
          const card = document.createElement("article");
          card.className = "supplemental-item";
          const link = document.createElement("a");
          link.href = item.url;
          link.textContent = item.nome;
          link.rel = "noopener";
          const metadata = document.createElement("small");
          metadata.textContent = dateLabel(item.criadoEm) + " · " + Math.ceil(item.tamanho / 1024) + " KB · manutenção #" + item.historicoId;
          card.append(link, metadata);
          list.append(card);
        });
      }
    } catch (error) {
      list.textContent = error.message;
    }
  }

  async function submitMaintenanceAttachment(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const status = document.querySelector("#maintenance-attachment-status");
    const fileInput = form.elements.arquivo;
    if (!fileInput.files?.length) {
      status.textContent = "Selecione um arquivo.";
      return;
    }
    if (fileInput.files[0].size > 5 * 1024 * 1024) {
      status.textContent = "O arquivo ultrapassa o limite de 5 MB.";
      return;
    }
    const body = new FormData(form);
    status.textContent = "Validando e armazenando o anexo…";
    try {
      await fetchJson("api/anexos-manutencao", {
        method:"POST",
        headers:{"X-CSRF-Token":currentUser?.csrf || ""},
        body
      });
      status.textContent = "Anexo armazenado no passaporte.";
      form.elements.arquivo.value = "";
      await loadMaintenanceAttachmentHistory();
    } catch (error) {
      status.textContent = error.message;
    }
  }

  async function exportMaintenanceHistory() {
    const status = document.querySelector("#maintenance-export-status");
    const selectedId = document.querySelector("#garage-vehicle-select")?.value;
    if (!selectedId) {
      status.textContent = "Selecione um veículo na seção Garagem Inteligente antes de exportar.";
      return;
    }
    status.textContent = "Preparando o histórico de manutenção…";
    try {
      const [vehicles, records] = await Promise.all([
        fetchJson("api/veiculos"),
        fetchJson("api/manutencoes?veiculoId=" + encodeURIComponent(selectedId))
      ]);
      const vehicle = vehicles.find(item => String(item.id) === String(selectedId));
      if (!vehicle) throw new Error("O veículo selecionado não foi encontrado.");
      const rows = records.map(item => `<tr><td>${escapeHtml(dateLabel(item.dataServico))}</td><td>${escapeHtml(item.categoria)}</td><td>${escapeHtml(item.descricao)}</td><td>${escapeHtml(Number(item.quilometragem || 0).toLocaleString("pt-BR") + " km")}</td><td>${escapeHtml(money(item.custo))}</td><td>${escapeHtml(item.oficina || "Não informada")}</td><td>${escapeHtml(item.proximaData ? dateLabel(item.proximaData) : "Não informada")}</td></tr>`).join("");
      const printWindow = window.open("", "_blank");
      if (!printWindow) throw new Error("O navegador bloqueou a janela. Permita pop-ups para exportar o histórico.");
      printWindow.document.write(`<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>Passaporte de manutenção</title><style>body{font:14px Arial,sans-serif;color:#222;margin:28px}h1{font-size:22px}p{line-height:1.5}table{width:100%;border-collapse:collapse;margin-top:18px}th,td{border:1px solid #bbb;padding:7px;text-align:left;vertical-align:top;font-size:11px}th{background:#eee}@media print{body{margin:12mm}button{display:none}}</style></head><body><h1>LaunchControl — Passaporte de manutenção</h1><p><strong>Veículo:</strong> ${escapeHtml([vehicle.marca,vehicle.modelo,vehicle.ano].filter(Boolean).join(" "))}</p><p><strong>Quilometragem atual registrada:</strong> ${escapeHtml(vehicle.quilometragem == null ? "Não informada" : Number(vehicle.quilometragem).toLocaleString("pt-BR") + " km")}</p><p>Este documento reúne registros informados no sistema. Não substitui notas fiscais, laudos ou inspeção profissional.</p><table><thead><tr><th>Data</th><th>Categoria</th><th>Serviço</th><th>Quilometragem</th><th>Custo</th><th>Oficina</th><th>Próxima manutenção</th></tr></thead><tbody>${rows || "<tr><td colspan='7'>Nenhum registro de manutenção encontrado.</td></tr>"}</tbody></table><script>window.addEventListener("load",()=>window.print());</script></body></html>`);
      printWindow.document.close();
      status.textContent = "Documento preparado. Na janela de impressão, selecione “Salvar como PDF”.";
    } catch (error) {
      status.textContent = error.message;
    }
  }

  window.refreshSupplementalFeatures = async function() {
    buildPanel();
    const panel = document.querySelector("#supplemental-tools");
    if (!currentUser || !["CLIENTE","OFICINA","ADMINISTRADOR"].includes(currentUser.perfil)) {
      panel.hidden = true;
      return;
    }
    panel.hidden = false;
    const proposalForm = document.querySelector("#additional-service-form");
    proposalForm.hidden = !["OFICINA","ADMINISTRADOR"].includes(currentUser.perfil);
    document.querySelector("#maintenance-export").closest(".supplemental-section").hidden = currentUser.perfil !== "CLIENTE";
    const tasks = [loadAdditionalServices(), loadNotifications()];
    if (!proposalForm.hidden) tasks.push(loadOrdersForProposal());
    if (currentUser.perfil === "CLIENTE") tasks.push(loadMaintenanceAttachmentVehicles());
    const results = await Promise.allSettled(tasks);
    const failed = results.find(result => result.status === "rejected");
    if (failed) {
      const notificationList = document.querySelector("#notification-list");
      if (notificationList && failed.reason) notificationList.textContent = failed.reason.message || "Não foi possível atualizar os dados.";
    }
  };

  document.addEventListener("DOMContentLoaded", () => {
    buildPanel();
    const refresh = () => {
      if (typeof currentUser !== "undefined" && currentUser) window.refreshSupplementalFeatures();
    };
    refresh();
    setTimeout(refresh, 700);
  });
})();
