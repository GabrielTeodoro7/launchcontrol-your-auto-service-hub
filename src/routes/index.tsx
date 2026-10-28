import { createFileRoute } from "@tanstack/react-router";

// Página de entrada do repositório. A aplicação executável em Java fica no módulo
// launchcontrol-servlet; esta página ajuda a encontrar o código e as instruções.
export const Route = createFileRoute("/")({
  component: Index,
});

function Index() {
  return (
    <main className="min-h-screen bg-slate-950 px-6 py-16 text-slate-100">
      <div className="mx-auto max-w-4xl">
        <p className="text-sm font-semibold uppercase tracking-[0.2em] text-orange-400">
          Plataforma de serviços automotivos
        </p>
        <h1 className="mt-4 text-4xl font-bold tracking-tight sm:text-6xl">
          LaunchControl
        </h1>
        <p className="mt-6 max-w-2xl text-lg leading-8 text-slate-300">
          Um projeto para aproximar clientes, oficinas e fornecedores, organizando
          veículos, solicitações de orçamento e acompanhamento de serviços.
        </p>

        <section className="mt-12 grid gap-4 sm:grid-cols-2">
          <a
            className="rounded-xl border border-slate-700 bg-slate-900 p-6 transition hover:border-orange-400"
            href="https://github.com/GabrielTeodoro7/launchcontrol-your-auto-service-hub/tree/main/launchcontrol-servlet"
          >
            <h2 className="text-xl font-semibold">Código da aplicação Java</h2>
            <p className="mt-2 text-sm leading-6 text-slate-300">
              Consulte os Servlets, a interface HTML/CSS/JavaScript e o esquema MySQL.
            </p>
            <span className="mt-4 inline-block text-sm font-semibold text-orange-400">
              Abrir código →
            </span>
          </a>

          <a
            className="rounded-xl border border-slate-700 bg-slate-900 p-6 transition hover:border-orange-400"
            href="https://github.com/GabrielTeodoro7/launchcontrol-your-auto-service-hub/blob/main/launchcontrol-servlet/README.md"
          >
            <h2 className="text-xl font-semibold">Como executar</h2>
            <p className="mt-2 text-sm leading-6 text-slate-300">
              Veja os requisitos, configure o banco e compile o arquivo WAR para um servidor Servlet.
            </p>
            <span className="mt-4 inline-block text-sm font-semibold text-orange-400">
              Ler instruções →
            </span>
          </a>
        </section>

        <p className="mt-10 text-sm text-slate-400">
          A interface e o backend Java precisam ser executados com MySQL e um servidor
          compatível com Jakarta Servlet 6. Esta página é apenas a entrada para o código
          e a documentação do projeto.
        </p>
      </div>
    </main>
  );
}
