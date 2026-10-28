package br.com.launchcontrol;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.*;

/** Permite ao usuário controlar canais externos sem expor tokens de integração. */
@WebServlet("/api/notificacoes/preferencias")
public class NotificationPreferencesServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res)throws IOException{
        HttpSession s=req.getSession(false);if(s==null){Json.error(res,401,"Autenticação necessária.");return;}long uid=(Long)s.getAttribute("userId");
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT COALESCE(p.email_ativo,TRUE),COALESCE(p.whatsapp_ativo,FALSE),p.whatsapp_numero,u.email FROM usuarios u LEFT JOIN preferencias_notificacao p ON p.usuario_id=u.id WHERE u.id=?")){
            p.setLong(1,uid);try(ResultSet r=p.executeQuery()){if(!r.next()){Json.error(res,404,"Conta não encontrada.");return;}res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"emailAtivo\":"+r.getBoolean(1)+",\"whatsappAtivo\":"+r.getBoolean(2)+",\"whatsappNumero\":"+Json.quote(r.getString(3))+",\"email\":"+Json.quote(r.getString(4))+"}");}
        }catch(SQLException e){Json.error(res,500,"Não foi possível consultar preferências. Confirme a migração 003.");}
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res)throws IOException{
        HttpSession s=req.getSession(false);if(s==null){Json.error(res,401,"Autenticação necessária.");return;}long uid=(Long)s.getAttribute("userId");
        boolean email=Boolean.parseBoolean(req.getParameter("emailAtivo"));boolean whatsapp=Boolean.parseBoolean(req.getParameter("whatsappAtivo"));
        String phone=req.getParameter("whatsappNumero");phone=phone==null?"":phone.replaceAll("[^0-9+]","");
        if(whatsapp&&(phone.length()<10||phone.length()>16||!phone.matches("\\+?[0-9]+"))){Json.error(res,400,"Informe o WhatsApp com código do país, DDD e número, apenas dígitos e opcionalmente +.");return;}
        if(phone.length()>20){Json.error(res,400,"Número de WhatsApp inválido.");return;}
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try(PreparedStatement p=c.prepareStatement("INSERT INTO preferencias_notificacao(usuario_id,email_ativo,whatsapp_ativo,whatsapp_numero) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE email_ativo=VALUES(email_ativo),whatsapp_ativo=VALUES(whatsapp_ativo),whatsapp_numero=VALUES(whatsapp_numero)")){
                p.setLong(1,uid);p.setBoolean(2,email);p.setBoolean(3,whatsapp);p.setString(4,phone.isBlank()?null:phone);p.executeUpdate();
            }
            if(whatsapp)try(PreparedStatement p=c.prepareStatement("UPDATE notificacao_entregas SET status='PENDENTE',proxima_tentativa_em=NOW() WHERE usuario_id=? AND canal='WHATSAPP' AND status='AGUARDANDO_CONFIGURACAO'")){p.setLong(1,uid);p.executeUpdate();}
            if(email)try(PreparedStatement p=c.prepareStatement("UPDATE notificacao_entregas SET status='PENDENTE',proxima_tentativa_em=NOW() WHERE usuario_id=? AND canal='EMAIL' AND status='AGUARDANDO_CONFIGURACAO'")){p.setLong(1,uid);p.executeUpdate();}
            c.commit();res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"ok\":true,\"mensagem\":\"Preferências salvas.\"}");
        }catch(SQLException e){Json.error(res,500,"Não foi possível salvar preferências.");}
    }
}
