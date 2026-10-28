const list = document.querySelector("#service-list");
const statusText = document.querySelector("#status");
const searchInput = document.querySelector("#service-search");
let searchTimer;

function createCard(service) {
  const card = document.createElement("article");
  card.className = "service-card";
  const title = document.createElement("h3");
  title.textContent = service.nome;
  const description = document.createElement("p");
  description.textContent = service.descricao || service.categoria || "Serviço automotivo";
  const price = document.createElement("div");
  price.className = "price";
  price.textContent = service.precoBase == null
    ? "Consulte o valor"
    : Number(service.precoBase).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
  card.append(title, description, price);
  return card;
}

async function loadServices(query = "") {
  statusText.textContent = "Carregando serviços…";
  list.replaceChildren();
  try {
    const response = await fetch("api/servicos?q=" + encodeURIComponent(query));
    if (!response.ok) throw new Error("Falha na consulta");
    const services = await response.json();
    if (services.length === 0) {
      statusText.textContent = "Nenhum serviço encontrado. Tente outro termo ou cadastre serviços no banco.";
      return;
    }
    services.forEach(service => list.append(createCard(service)));
    statusText.textContent = services.length + (services.length === 1 ? " serviço encontrado." : " serviços encontrados.");
  } catch {
    statusText.textContent = "Não foi possível carregar o catálogo. Verifique se o servidor Java e o banco MySQL estão configurados.";
  }
}

searchInput.addEventListener("input", () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => loadServices(searchInput.value.trim()), 250);
});
loadServices();
