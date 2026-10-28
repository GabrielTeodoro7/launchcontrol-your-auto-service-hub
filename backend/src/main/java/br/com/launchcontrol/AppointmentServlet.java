package br.com.launchcontrol;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/** Gerencia horários de oficina e reserva vagas sem sobreposição concorrente. */
@WebServlet("/api/agendamentos")
public class AppointmentServlet extends HttpServlet {
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final Set<String> ACTIVE = Set.of("AGENDADO", "CONFIRMADO");

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession s = req.getSession(false);
        if (s == null) { Json.error(res, 401, "Autenticação necessária."); return; }
        String role = (String) s.getAttribute("userRole");
        long uid = (Long) s.getAttribute("userId");
        try (Connection c = Database.getConnection()) {
            if ("1".equals(req.getParameter("oficinas"))) {
                String sql = "SELECT f.id,f.nome_fantasia,f.cidade,f.estado FROM oficinas f WHERE f.ativa=TRUE ORDER BY f.nome_fantasia LIMIT 300";
                try (PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
                    StringBuilder out = new StringBuilder("["); boolean first = true;
                    while (r.next()) {
                        if (!first) out.append(','); first = false;
                        out.append("{\"id\":").append(r.getLong(1)).append(",\"nome\":").append(Json.quote(r.getString(2)))
                           .append(",\"cidade\":").append(Json.quote(r.getString(3))).append(",\"estado\":").append(Json.quote(r.getString(4))).append('}');
                    }
                    json(res, out.append(']').toString()); return;
                }
            }
            String sql = "SELECT a.id,a.codigo,a.oficina_id,a.cliente_id,a.veiculo_id,a.inicio_em,a.fim_em,a.status,a.observacoes,"
                    + "f.nome_fantasia,v.marca,v.modelo FROM agendamentos a JOIN oficinas f ON f.id=a.oficina_id "
                    + "JOIN veiculos v ON v.id=a.veiculo_id ";
            if ("CLIENTE".equals(role)) sql += "WHERE a.cliente_id=? ";
            else if ("OFICINA".equals(role)) sql += "WHERE f.usuario_id=? ";
            else if (!"ADMINISTRADOR".equals(role)) { Json.error(res,403,"Perfil sem acesso à agenda."); return; }
            sql += "ORDER BY a.inicio_em DESC LIMIT 250";
            try (PreparedStatement p = c.prepareStatement(sql)) {
                if (!"ADMINISTRADOR".equals(role)) p.setLong(1, uid);
                try (ResultSet r = p.executeQuery()) {
                    StringBuilder out = new StringBuilder("["); boolean first = true;
                    while (r.next()) {
                        if (!first) out.append(','); first = false;
                        out.append("{\"id\":").append(r.getLong("id")).append(",\"codigo\":").append(Json.quote(r.getString("codigo")))
                           .append(",\"oficinaId\":").append(r.getLong("oficina_id")).append(",\"clienteId\":").append(r.getLong("cliente_id"))
                           .append(",\"veiculoId\":").append(r.getLong("veiculo_id")).append(",\"inicio\":").append(Json.quote(r.getString("inicio_em")))
                           .append(",\"fim\":").append(Json.quote(r.getString("fim_em"))).append(",\"status\":").append(Json.quote(r.getString("status")))
                           .append(",\"observacoes\":").append(Json.quote(r.getString("observacoes"))).append(",\"oficina\":").append(Json.quote(r.getString("nome_fantasia")))
                           .append(",\"veiculo\":").append(Json.quote(r.getString("marca")+" "+r.getString("modelo"))).append('}');
                    }
                    json(res, out.append(']').toString());
                }
            }
        } catch (SQLException ex) { Json.error(res,500,"Não foi possível consultar a agenda. Confirme a migração 003."); }
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession s=req.getSession(false);
        if(s==null){Json.error(res,401,"Autenticação necessária.");return;}
        String role=(String)s.getAttribute("userRole"); long uid=(Long)s.getAttribute("userId");
        String action=req.getParameter("acao");
        if("configurar".equals(action)){ configure(req,res,role,uid); return; }
        if("cancelar".equals(action)){ cancel(req,res,role,uid); return; }
        if(!"CLIENTE".equals(role)){Json.error(res,403,"Somente clientes podem solicitar agendamentos.");return;}
        long officeId,vehicleId; LocalDateTime start,end; String notes=req.getParameter("observacoes");
        try {
            officeId=Long.parseLong(req.getParameter("oficinaId")); vehicleId=Long.parseLong(req.getParameter("veiculoId"));
            start=LocalDateTime.parse(req.getParameter("inicio"),INPUT);
            int minutes=Integer.parseInt(req.getParameter("duracaoMinutos"));
            if(minutes<30||minutes>240||start.isBefore(LocalDateTime.now().plusMinutes(30))||start.isAfter(LocalDateTime.now().plusDays(60))) throw new IllegalArgumentException();
            end=start.plusMinutes(minutes);
            if (!start.toLocalDate().equals(end.toLocalDate())) throw new IllegalArgumentException();
            if(notes!=null&&notes.length()>500) throw new IllegalArgumentException();
        } catch(Exception e){Json.error(res,400,"Informe oficina, veículo, data futura (30 minutos a 60 dias), duração entre 30 e 240 minutos e intervalo dentro do mesmo dia.");return;}
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("SELECT id FROM veiculos WHERE id=? AND cliente_id=? FOR UPDATE")){
                    p.setLong(1,vehicleId);p.setLong(2,uid);try(ResultSet r=p.executeQuery()){if(!r.next()){c.rollback();Json.error(res,403,"O veículo selecionado não pertence à sua conta.");return;}}
                }
                int day=start.getDayOfWeek().getValue(); LocalTime st=start.toLocalTime(), en=end.toLocalTime();
                int capacity;
                try(PreparedStatement p=c.prepareStatement("SELECT capacidade FROM configuracao_agenda_oficina WHERE oficina_id=? AND dia_semana=? AND ativo=TRUE AND hora_inicio<=? AND hora_fim>=? FOR UPDATE")){
                    p.setLong(1,officeId);p.setInt(2,day);p.setTime(3,Time.valueOf(st));p.setTime(4,Time.valueOf(en));
                    try(ResultSet r=p.executeQuery()){if(!r.next()){c.rollback();Json.error(res,409,"A oficina não atende nesse dia/horário ou o intervalo excede o expediente cadastrado.");return;}capacity=r.getInt(1);}
                }
                try(PreparedStatement p=c.prepareStatement("SELECT id FROM oficinas WHERE id=? AND ativa=TRUE FOR UPDATE")){
                    p.setLong(1,officeId);try(ResultSet r=p.executeQuery()){if(!r.next()){c.rollback();Json.error(res,404,"Oficina não encontrada ou inativa.");return;}}
                }
                int occupied;
                try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM agendamentos WHERE oficina_id=? AND status IN ('AGENDADO','CONFIRMADO') AND inicio_em<? AND fim_em>?")){
                    p.setLong(1,officeId);p.setTimestamp(2,Timestamp.valueOf(end));p.setTimestamp(3,Timestamp.valueOf(start));
                    try(ResultSet r=p.executeQuery()){r.next();occupied=r.getInt(1);}
                }
                if(occupied>=capacity){c.rollback();Json.error(res,409,"Não há capacidade disponível nesse intervalo. Escolha outro horário.");return;}
                String code="AG-"+java.util.UUID.randomUUID().toString().substring(0,8).toUpperCase(java.util.Locale.ROOT);
                long id;
                try(PreparedStatement p=c.prepareStatement("INSERT INTO agendamentos(codigo,oficina_id,cliente_id,veiculo_id,inicio_em,fim_em,observacoes) VALUES(?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                    p.setString(1,code);p.setLong(2,officeId);p.setLong(3,uid);p.setLong(4,vehicleId);p.setTimestamp(5,Timestamp.valueOf(start));p.setTimestamp(6,Timestamp.valueOf(end));p.setString(7,notes==null||notes.isBlank()?null:notes.trim());p.executeUpdate();
                    try(ResultSet r=p.getGeneratedKeys()){r.next();id=r.getLong(1);}
                }
                audit(c,uid,"AGENDAMENTO_CRIADO","agendamentos",id,"Agendamento "+code+" solicitado.");
                c.commit();res.setStatus(201);json(res,"{\"ok\":true,\"id\":"+id+",\"codigo\":"+Json.quote(code)+"}");
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
        }catch(SQLException e){Json.error(res,500,"Não foi possível registrar o agendamento.");}
    }

    private void configure(HttpServletRequest req,HttpServletResponse res,String role,long uid)throws IOException{
        if(!"OFICINA".equals(role)&&!"ADMINISTRADOR".equals(role)){Json.error(res,403,"Somente oficinas podem configurar horários.");return;}
        int day,capacity; long officeId; LocalTime start,end;
        try{day=Integer.parseInt(req.getParameter("diaSemana"));capacity=Integer.parseInt(req.getParameter("capacidade"));start=LocalTime.parse(req.getParameter("horaInicio"));end=LocalTime.parse(req.getParameter("horaFim"));officeId=officeForUser(uid,role,req.getParameter("oficinaId"));if(day<1||day>7||capacity<1||capacity>50||!end.isAfter(start))throw new IllegalArgumentException();}
        catch(Exception e){Json.error(res,400,"Confira dia da semana, horários, capacidade e oficina.");return;}
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO configuracao_agenda_oficina(oficina_id,dia_semana,hora_inicio,hora_fim,capacidade,ativo) VALUES(?,?,?,?,?,TRUE) ON DUPLICATE KEY UPDATE hora_inicio=VALUES(hora_inicio),hora_fim=VALUES(hora_fim),capacidade=VALUES(capacidade),ativo=TRUE")){
            p.setLong(1,officeId);p.setInt(2,day);p.setTime(3,Time.valueOf(start));p.setTime(4,Time.valueOf(end));p.setInt(5,capacity);p.executeUpdate();
            auditSimple(uid,"AGENDA_CONFIGURADA","configuracao_agenda_oficina",officeId,"Dia "+day+", capacidade "+capacity);
            json(res,"{\"ok\":true,\"mensagem\":\"Horário salvo.\"}");
        }catch(SQLException e){Json.error(res,500,"Não foi possível salvar o horário.");}
    }
    private long officeForUser(long uid,String role,String raw)throws SQLException{
        String sql="SELECT id FROM oficinas WHERE "+("ADMINISTRADOR".equals(role)?"id=?":"usuario_id=?");
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setLong(1,"ADMINISTRADOR".equals(role)?Long.parseLong(raw):uid);
            try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Oficina não encontrada ou não pertence à conta.");return r.getLong(1);}
        }
    }
    private void cancel(HttpServletRequest req,HttpServletResponse res,String role,long uid)throws IOException{
        long id;try{id=Long.parseLong(req.getParameter("id"));}catch(Exception e){Json.error(res,400,"Agendamento inválido.");return;}
        String sql="UPDATE agendamentos a JOIN oficinas f ON f.id=a.oficina_id SET a.status='CANCELADO' WHERE a.id=? AND a.status IN ('AGENDADO','CONFIRMADO') AND "+("ADMINISTRADOR".equals(role)?"1=1":"(a.cliente_id=? OR f.usuario_id=?)");
        if(!"CLIENTE".equals(role)&&!"OFICINA".equals(role)&&!"ADMINISTRADOR".equals(role)){Json.error(res,403,"Perfil sem acesso.");return;}
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,id);if(!"ADMINISTRADOR".equals(role)){p.setLong(2,uid);p.setLong(3,uid);}int changed=p.executeUpdate();if(changed==0){Json.error(res,404,"Agendamento não encontrado ou já encerrado.");return;}auditSimple(uid,"AGENDAMENTO_CANCELADO","agendamentos",id,"Cancelamento solicitado.");json(res,"{\"ok\":true}");}
        catch(SQLException e){Json.error(res,500,"Não foi possível cancelar o agendamento.");}
    }
    private static void audit(Connection c,long uid,String action,String entity,long id,String details)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("INSERT INTO auditoria(usuario_id,acao,entidade,entidade_id,detalhes) VALUES(?,?,?,?,?)")){p.setLong(1,uid);p.setString(2,action);p.setString(3,entity);p.setLong(4,id);p.setString(5,details);p.executeUpdate();}
    }
    private static void auditSimple(long uid,String action,String entity,long id,String details)throws SQLException{
        try(Connection c=Database.getConnection()){audit(c,uid,action,entity,id,details);}
    }
    private static void json(HttpServletResponse res,String body)throws IOException{res.setContentType("application/json;charset=UTF-8");res.setHeader("Cache-Control","no-store");res.getWriter().write(body);}
}
