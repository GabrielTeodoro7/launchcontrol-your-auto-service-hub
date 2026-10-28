-- SOMENTE PARA CI/TESTES: contas e dados sintéticos. NUNCA executar em produção.
USE launchcontrol;

INSERT INTO usuarios (id,nome,email,senha_hash,perfil,ativo) VALUES
(900001,'Oficina Smoke Test','smoke-workshop@example.test','pbkdf2-sha256$210000$TGF1bmNoQ29udHJvbFRlc3RTYWx0$VW1fO17BeBr4oUxUOAEyZ/kjAcGrYDi5uPW06+hqwM8','OFICINA',TRUE),
(900002,'Fornecedor Smoke Test','smoke-supplier@example.test','pbkdf2-sha256$210000$TGF1bmNoQ29udHJvbFRlc3RTYWx0$VW1fO17BeBr4oUxUOAEyZ/kjAcGrYDi5uPW06+hqwM8','FORNECEDOR',TRUE),
(900003,'Administrador Smoke Test','smoke-admin@example.test','pbkdf2-sha256$210000$TGF1bmNoQ29udHJvbFRlc3RTYWx0$VW1fO17BeBr4oUxUOAEyZ/kjAcGrYDi5uPW06+hqwM8','ADMINISTRADOR',TRUE);

INSERT INTO oficinas (id,usuario_id,nome_fantasia,descricao,telefone,endereco,cidade,estado,ativa)
VALUES (900001,900001,'Oficina Smoke Test','Registro sintético para testes automatizados','61999990000','Endereço de teste','Brasília','DF',TRUE);

INSERT INTO fornecedores (id,usuario_id,nome_fantasia,telefone,cidade,estado,ativo)
VALUES (900001,900002,'Fornecedor Smoke Test','61999990001','Brasília','DF',TRUE);

INSERT INTO servicos (id,oficina_id,nome,descricao,preco_base,categoria,ativo)
VALUES (900001,900001,'Revisão Smoke Test','Serviço sintético usado somente no CI',150.00,'Revisão',TRUE);

INSERT INTO pecas (id,fornecedor_id,nome,marca,codigo,descricao,preco,estoque,ativo)
VALUES (900001,900001,'Filtro Smoke Test','LC Test','SMOKE-FILTER-001','Peça sintética usada somente no CI',45.00,20,TRUE);

INSERT INTO configuracao_agenda_oficina (oficina_id,dia_semana,hora_inicio,hora_fim,capacidade,ativo) VALUES
(900001,1,'00:00:00','23:59:00',2,TRUE),
(900001,2,'00:00:00','23:59:00',2,TRUE),
(900001,3,'00:00:00','23:59:00',2,TRUE),
(900001,4,'00:00:00','23:59:00',2,TRUE),
(900001,5,'00:00:00','23:59:00',2,TRUE),
(900001,6,'00:00:00','23:59:00',2,TRUE),
(900001,7,'00:00:00','23:59:00',2,TRUE);
