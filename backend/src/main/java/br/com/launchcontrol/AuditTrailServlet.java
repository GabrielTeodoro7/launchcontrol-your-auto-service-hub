package br.com.launchcontrol;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.*;

/** Exibe eventos de auditoria respeitando a propriedade dos registros. */
@WebServlet("/api/auditoria")
public class AuditTrailServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res)throws IOException{
        HttpSession s=req.getSession(false);if(s==null){Json.error(res,401,"Autenticação necessária.");return;}
        String role=(String)s.getAttribute("userRole");long uid=(Long)s.getAttribute("userId");
        if(!java.util.Set.of("ADMINISTRADOR","OFICINA","CLIENTE").contains(role)){Json.error(res,403,"Perfil sem acesso à auditoria.");return;}
        String sql="SELECT a.id,a.acao,a.entidade,a.entidade_id,a.detalhes,a.criado_em,u.nome FROM auditoria a LEFT JOIN usuarios u ON u.id=a.usuario_id ";
        if("OFICINA".equals(role))sql+="WHERE (a.entidade='orcamentos' AND a.entidade_id IN (SELECT o.id FROM orcamentos o JOIN oficinas f ON f.id=o.oficina_id WHERE f.usuario_id=?)) OR (a.entidade='agendamentos' AND a.entidade_id IN (SELECT ag.id FROM agendamentos ag JOIN oficinas f ON f.id=ag.oficina_id WHERE f.usuario_id=?)) ";
        else if("CLIENTE".equals(role))sql+="WHERE (a.entidade='orcamentos' AND a.entidade_id IN (SELECT o.id FROM orcamentos o JOIN solicitacoes_orcamento so ON so.id=o.solicitacao_id WHERE so.cliente_id=?)) OR (a.entidade='agendamentos' AND a.entidade_id IN (SELECT id FROM agendamentos WHERE cliente_id=?)) ";
        sql+="ORDER BY a.criado_em DESC,a.id DESC LIMIT 100";
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            if(!"ADMINISTRADOR".equals(role)){p.setLong(1,uid);if(!sql.contains("entidade='orcamentos' AND a.entidade_id IN (SELECT o.id")){} if(role.equals("OFICINA")||role.equals("CLIENTE"))p.setLong(2,uid);}
            try(ResultSet r=p.executeQuery()){StringBuilder out=new StringBuilder("[");boolean first=true;while(r.next()){if(!first)out.append(',');first=false;
                out.append("{\"id\":").append(r.getLong("id")).append(",\"acao\":").append(Json.quote(r.getString("acao")))
                .append(",\"entidade\":").append(Json.quote(r.getString("entidade"))).append(",\"entidadeId\":").append(r.getObject("entidade_id")==null?"null":r.getLong("entidade_id"))
                .append(",\"detalhes\":").append(Json.quote(r.getString("detalhes"))).append(",\"criadoEm\":").append(Json.quote(r.getString("criado_em")))
                .append(",\"responsavel\":").append(Json.quote(r.getString("nome"))).append('}');}
                res.setContentType("application/json;charset=UTF-8");res.setHeader("Cache-Control","no-store");res.getWriter().write(out.append(']').toString());}
        }catch(SQLException e){Json.error(res,500,"Não foi possível consultar a auditoria.");}
    }
}
