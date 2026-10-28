import { useMemo, useState, type FormEvent } from "react";
import { createFileRoute } from "@tanstack/react-router";
import {
  ArrowDownRight,
  ArrowRight,
  BadgeCheck,
  CarFront,
  Check,
  ChevronDown,
  CircleDollarSign,
  Clock3,
  Gauge,
  MapPin,
  Menu,
  Search,
  ShieldCheck,
  Sparkles,
  Star,
  Wrench,
  X,
} from "lucide-react";

export const Route = createFileRoute("/")({
  component: Index,
});

const categories = [
  { id: "oficinas", title: "Oficinas", description: "Encontre profissionais para cuidar do seu carro.", icon: Wrench },
  { id: "pecas", title: "Peças automotivas", description: "Compare opções e encontre a peça certa.", icon: Gauge },
  { id: "orcamentos", title: "Orçamentos", description: "Organize propostas antes de decidir.", icon: CircleDollarSign },
  { id: "diagnostico", title: "Diagnóstico", description: "Descreva os sintomas para orientar o atendimento.", icon: CarFront },
];

const demoResults = [
  { id: "oficinas", name: "Oficinas especializadas", detail: "Serviços de manutenção e reparação", label: "Serviços" },
  { id: "pecas", name: "Peças para seu veículo", detail: "Peças e acessórios automotivos", label: "Peças" },
  { id: "orcamentos", name: "Comparar orçamentos", detail: "Visualize propostas lado a lado", label: "Orçamentos" },
  { id: "diagnostico", name: "Relatar um problema", detail: "Registre sintomas e informações do veículo", label: "Diagnóstico" },
];

function Index() {
  const [query, setQuery] = useState("");
  const [showMenu, setShowMenu] = useState(false);
  const [searched, setSearched] = useState(false);

  const results = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase("pt-BR");
    if (!normalized) return demoResults;
    return demoResults.filter((item) =>
      `${item.name} ${item.detail} ${item.label}`.toLocaleLowerCase("pt-BR").includes(normalized),
    );
  }, [query]);

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSearched(true);
    document.getElementById("explorar")?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  return (
    <main className="min-h-screen overflow-hidden bg-[#0b0e12] text-[#f5f6f7]">
      <header className="relative z-20 border-b border-white/10 bg-[#0b0e12]/95">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-5 py-4 sm:px-8">
          <a href="#inicio" className="flex items-center gap-3" aria-label="LaunchControl, início">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-orange-500 text-[#101114]">
              <Wrench size={21} strokeWidth={2.5} />
            </span>
            <span className="leading-tight">
              <span className="block text-lg font-black tracking-tight">LAUNCH<span className="text-orange-400">CONTROL</span></span>
              <span className="block text-[10px] font-semibold uppercase tracking-[0.22em] text-slate-500">Plataforma automotiva</span>
            </span>
          </a>

          <nav className="hidden items-center gap-8 text-sm text-slate-300 md:flex" aria-label="Navegação principal">
            <a className="transition hover:text-orange-300" href="#explorar">Explorar</a>
            <a className="transition hover:text-orange-300" href="#como-funciona">Como funciona</a>
            <a className="transition hover:text-orange-300" href="#prestadores">Para prestadores</a>
          </nav>

          <div className="hidden items-center gap-3 md:flex">
            <a href="#acesso" className="rounded-lg px-4 py-2.5 text-sm font-semibold text-slate-200 transition hover:bg-white/5">Entrar</a>
            <a href="#acesso" className="rounded-lg bg-orange-500 px-4 py-2.5 text-sm font-bold text-[#15120f] transition hover:bg-orange-400">Começar agora <ArrowRight className="ml-1 inline" size={16} /></a>
          </div>

          <button type="button" className="rounded-lg border border-white/10 p-2 text-slate-200 md:hidden" aria-label={showMenu ? "Fechar menu" : "Abrir menu"} aria-expanded={showMenu} onClick={() => setShowMenu(!showMenu)}>
            {showMenu ? <X size={22} /> : <Menu size={22} />}
          </button>
        </div>
        {showMenu && (
          <nav className="grid gap-1 border-t border-white/10 px-5 py-3 md:hidden" aria-label="Menu móvel">
            <a className="rounded-lg px-3 py-3 text-sm hover:bg-white/5" href="#explorar" onClick={() => setShowMenu(false)}>Explorar</a>
            <a className="rounded-lg px-3 py-3 text-sm hover:bg-white/5" href="#como-funciona" onClick={() => setShowMenu(false)}>Como funciona</a>
            <a className="rounded-lg px-3 py-3 text-sm hover:bg-white/5" href="#prestadores" onClick={() => setShowMenu(false)}>Para prestadores</a>
            <a className="rounded-lg bg-orange-500 px-3 py-3 text-sm font-bold text-[#15120f]" href="#acesso" onClick={() => setShowMenu(false)}>Acessar plataforma</a>
          </nav>
        )}
      </header>

      <section id="inicio" className="relative isolate">
        <div className="pointer-events-none absolute -right-40 -top-28 -z-10 h-[32rem] w-[32rem] rounded-full bg-orange-500/10 blur-[100px]" />
        <div className="mx-auto grid max-w-7xl items-center gap-12 px-5 pb-20 pt-16 sm:px-8 sm:pb-24 sm:pt-24 lg:grid-cols-[1.05fr_0.95fr] lg:gap-16 lg:pb-28 lg:pt-28">
          <div>
            <div className="mb-7 inline-flex items-center gap-2 rounded-full border border-orange-400/25 bg-orange-400/10 px-3.5 py-2 text-xs font-semibold text-orange-200">
              <Sparkles size={14} /> Cuidado automotivo, com mais clareza
            </div>
            <h1 className="max-w-3xl text-4xl font-black leading-[1.07] tracking-[-0.04em] sm:text-5xl lg:text-6xl">
              Seu carro merece cuidado.
              <span className="mt-2 block text-orange-400">Sua escolha merece confiança.</span>
            </h1>
            <p className="mt-6 max-w-xl text-base leading-7 text-slate-400 sm:text-lg sm:leading-8">
              Encontre serviços automotivos, organize solicitações e compare orçamentos em um só lugar. Mais informação para decidir com tranquilidade.
            </p>

            <form onSubmit={handleSearch} className="mt-9 flex max-w-2xl flex-col gap-2 rounded-2xl border border-white/10 bg-[#151a20] p-2 shadow-2xl shadow-black/20 sm:flex-row">
              <label className="flex min-w-0 flex-1 items-center gap-3 px-3 py-2">
                <Search size={20} className="shrink-0 text-orange-400" />
                <span className="sr-only">O que você está procurando?</span>
                <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Ex.: revisão, freios, peças..." className="min-w-0 flex-1 bg-transparent text-sm text-white outline-none placeholder:text-slate-500" />
              </label>
              <button type="submit" className="rounded-xl bg-orange-500 px-5 py-3.5 text-sm font-extrabold text-[#15120f] transition hover:bg-orange-400">
                Explorar opções <ArrowRight className="ml-1 inline" size={16} />
              </button>
            </form>
            <p className="mt-3 flex items-center gap-2 text-xs text-slate-500"><ShieldCheck size={14} className="text-emerald-400" /> Compare informações antes de escolher um prestador.</p>

            <div className="mt-10 flex flex-wrap gap-x-6 gap-y-3 border-t border-white/10 pt-6 text-xs font-medium text-slate-400">
              <span className="inline-flex items-center gap-2"><Check size={15} className="text-orange-400" /> Serviços em um só lugar</span>
              <span className="inline-flex items-center gap-2"><Check size={15} className="text-orange-400" /> Orçamentos organizados</span>
              <span className="inline-flex items-center gap-2"><Check size={15} className="text-orange-400" /> Histórico de atendimento</span>
            </div>
          </div>

          <div className="relative mx-auto w-full max-w-xl">
            <div className="absolute -inset-5 rounded-[2rem] bg-orange-500/10 blur-2xl" />
            <div className="relative overflow-hidden rounded-[1.75rem] border border-white/10 bg-[#13181e] p-5 shadow-2xl shadow-black/30 sm:p-7">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-500">Seu painel automotivo</p>
                  <h2 className="mt-2 text-2xl font-bold">Tudo mais organizado.</h2>
                </div>
                <span className="rounded-xl border border-orange-400/20 bg-orange-400/10 p-3 text-orange-300"><CarFront size={24} /></span>
              </div>
              <div className="mt-7 grid grid-cols-2 gap-3">
                <div className="rounded-2xl border border-white/8 bg-[#1a2027] p-4">
                  <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-400/10 text-blue-300"><Search size={18} /></span>
                  <p className="mt-4 text-sm font-bold">Encontre</p>
                  <p className="mt-1 text-xs leading-5 text-slate-500">Oficinas, serviços e peças</p>
                </div>
                <div className="rounded-2xl border border-white/8 bg-[#1a2027] p-4">
                  <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-orange-400/10 text-orange-300"><CircleDollarSign size={18} /></span>
                  <p className="mt-4 text-sm font-bold">Compare</p>
                  <p className="mt-1 text-xs leading-5 text-slate-500">Propostas e condições</p>
                </div>
                <div className="rounded-2xl border border-white/8 bg-[#1a2027] p-4">
                  <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-400/10 text-emerald-300"><Clock3 size={18} /></span>
                  <p className="mt-4 text-sm font-bold">Acompanhe</p>
                  <p className="mt-1 text-xs leading-5 text-slate-500">Etapas do atendimento</p>
                </div>
                <div className="rounded-2xl border border-white/8 bg-[#1a2027] p-4">
                  <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-violet-400/10 text-violet-300"><BadgeCheck size={18} /></span>
                  <p className="mt-4 text-sm font-bold">Decida</p>
                  <p className="mt-1 text-xs leading-5 text-slate-500">Com mais informações</p>
                </div>
              </div>
              <div className="mt-4 flex items-center gap-3 rounded-xl border border-white/8 bg-[#0f1318] p-4">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-orange-500 text-[#15120f]"><MapPin size={18} /></div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold">Sua próxima manutenção</p>
                  <p className="mt-1 text-xs text-slate-500">Pesquise opções e compare propostas disponíveis.</p>
                </div>
                <ArrowDownRight size={18} className="text-orange-400" />
              </div>
              <p className="mt-4 text-[11px] leading-5 text-slate-600">Prévia ilustrativa da proposta de experiência. Cadastros, disponibilidade e orçamentos reais dependem da integração com o backend.</p>
            </div>
          </div>
        </div>
      </section>

      <section id="explorar" className="scroll-mt-8 border-y border-white/8 bg-[#101419] py-16 sm:py-20">
        <div className="mx-auto max-w-7xl px-5 sm:px-8">
          <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.2em] text-orange-400">Explore o LaunchControl</p>
              <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">O que seu carro precisa hoje?</h2>
              <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-400 sm:text-base">Comece pelo que você procura e encontre o caminho certo para organizar seu atendimento.</p>
            </div>
            <span className="w-fit rounded-full border border-white/10 px-3 py-1.5 text-xs text-slate-500">Área de demonstração</span>
          </div>

          {searched && (
            <div className="mt-6 rounded-xl border border-orange-400/20 bg-orange-400/5 px-4 py-3 text-sm text-orange-100" role="status">
              {query.trim() ? `Resultados demonstrativos para “${query.trim()}”.` : "Exibindo todas as opções demonstrativas."} Esta busca ainda não consulta uma base de dados real.
            </div>
          )}

          <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {categories.filter((category) => {
              const normalized = query.trim().toLocaleLowerCase("pt-BR");
              return !searched || !normalized || `${category.title} ${category.description}`.toLocaleLowerCase("pt-BR").includes(normalized) || results.some((result) => result.id === category.id);
            }).map((category) => {
              const Icon = category.icon;
              return (
                <button key={category.id} type="button" onClick={() => { setQuery(category.title); setSearched(true); }} className="group rounded-2xl border border-white/8 bg-[#151a20] p-5 text-left transition hover:-translate-y-1 hover:border-orange-400/35 hover:bg-[#191f26]">
                  <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-orange-500/10 text-orange-300 transition group-hover:bg-orange-500 group-hover:text-[#15120f]"><Icon size={22} /></span>
                  <h3 className="mt-5 text-base font-bold">{category.title}</h3>
                  <p className="mt-2 min-h-10 text-sm leading-5 text-slate-400">{category.description}</p>
                  <span className="mt-5 inline-flex items-center gap-2 text-xs font-bold text-orange-300">Explorar <ArrowRight size={14} /></span>
                </button>
              );
            })}
          </div>

          <div className="mt-8 rounded-2xl border border-white/8 bg-[#0d1116] p-5 sm:p-6">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="text-sm font-bold">Atalhos de demonstração</p>
                <p className="mt-1 text-xs text-slate-500">Escolha uma área para filtrar a prévia da plataforma.</p>
              </div>
              <div className="flex flex-wrap gap-2">
                {results.map((result) => (
                  <button key={result.id} type="button" onClick={() => { setQuery(result.name); setSearched(true); }} className="rounded-full border border-white/10 px-3 py-2 text-xs font-semibold text-slate-300 transition hover:border-orange-400/30 hover:text-orange-200">{result.label}</button>
                ))}
                {results.length === 0 && <span className="text-sm text-slate-500">Nenhuma opção demonstrativa corresponde à busca.</span>}
              </div>
            </div>
          </div>
        </div>
      </section>

      <section id="como-funciona" className="py-16 sm:py-20">
        <div className="mx-auto max-w-7xl px-5 sm:px-8">
          <div className="max-w-2xl">
            <p className="text-xs font-bold uppercase tracking-[0.2em] text-orange-400">Simples e transparente</p>
            <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">Do problema à decisão, em etapas claras.</h2>
          </div>
          <div className="mt-10 grid gap-8 md:grid-cols-3">
            {[
              { n: "01", title: "Descreva o que precisa", body: "Informe seu veículo e conte qual serviço, peça ou problema você quer resolver." },
              { n: "02", title: "Organize as propostas", body: "Tenha uma visão estruturada das informações e das condições oferecidas." },
              { n: "03", title: "Escolha e acompanhe", body: "Compare alternativas e acompanhe o atendimento com mais clareza." },
            ].map((step) => (
              <article key={step.n} className="border-t border-white/10 pt-5">
                <span className="text-sm font-black tracking-widest text-orange-400">{step.n}</span>
                <h3 className="mt-4 text-xl font-bold">{step.title}</h3>
                <p className="mt-3 max-w-sm text-sm leading-6 text-slate-400">{step.body}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section id="prestadores" className="border-y border-white/8 bg-[#101419] py-16 sm:py-20">
        <div className="mx-auto grid max-w-7xl gap-8 px-5 sm:px-8 lg:grid-cols-[1fr_auto] lg:items-center">
          <div className="max-w-3xl">
            <p className="text-xs font-bold uppercase tracking-[0.2em] text-orange-400">Para oficinas e fornecedores</p>
            <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">Mais organização para quem cuida dos veículos.</h2>
            <p className="mt-4 max-w-2xl text-sm leading-7 text-slate-400 sm:text-base">A proposta do LaunchControl é aproximar clientes e prestadores, centralizando solicitações, propostas e informações de atendimento.</p>
          </div>
          <a href="#acesso" className="inline-flex w-fit items-center gap-2 rounded-xl border border-orange-400/40 px-5 py-3.5 text-sm font-bold text-orange-200 transition hover:bg-orange-400/10">Conhecer a plataforma <ArrowRight size={16} /></a>
        </div>
      </section>

      <section id="acesso" className="py-16 sm:py-20">
        <div className="mx-auto max-w-7xl px-5 sm:px-8">
          <div className="rounded-3xl border border-orange-400/20 bg-gradient-to-br from-[#1a1d20] to-[#101317] p-7 sm:p-10 lg:flex lg:items-center lg:justify-between lg:gap-10">
            <div className="max-w-2xl">
              <p className="text-xs font-bold uppercase tracking-[0.2em] text-orange-400">Próxima etapa</p>
              <h2 className="mt-3 text-2xl font-extrabold sm:text-3xl">Uma plataforma, quatro perfis de acesso.</h2>
              <p className="mt-3 text-sm leading-6 text-slate-400">Cliente, oficina, fornecedor e administrador terão fluxos próprios. O login e as permissões reais precisam ser conectados ao backend Java Servlet.</p>
            </div>
            <div className="mt-6 flex flex-wrap gap-2 lg:mt-0 lg:max-w-sm">
              {["Cliente", "Oficina", "Fornecedor", "Administrador"].map((role) => <span key={role} className="rounded-lg border border-white/10 bg-white/[0.03] px-3 py-2 text-xs font-semibold text-slate-300">{role}</span>)}
            </div>
          </div>
        </div>
      </section>

      <footer className="border-t border-white/10 bg-[#080a0d]">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-5 py-7 text-xs text-slate-500 sm:flex-row sm:items-center sm:justify-between sm:px-8">
          <div className="font-bold tracking-wide text-slate-300">LAUNCH<span className="text-orange-400">CONTROL</span></div>
          <p>Plataforma de Serviços Automotivos · Projeto em desenvolvimento</p>
          <a className="inline-flex items-center gap-1 transition hover:text-orange-300" href="#inicio">Voltar ao topo <Star size={12} /></a>
        </div>
      </footer>
    </main>
  );
}
