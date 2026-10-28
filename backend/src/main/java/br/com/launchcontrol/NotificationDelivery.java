package br.com.launchcontrol;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;

/** Processa uma fila persistente de entregas por Resend (e-mail) e WhatsApp Cloud API. */
final class NotificationDelivery {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private NotificationDelivery(){}
    static void processPending(){
        String resend=env("RESEND_API_KEY"),from=env("EMAIL_FROM");
        String waToken=env("WHATSAPP_ACCESS_TOKEN"),waPhoneId=env("WHATSAPP_PHONE_NUMBER_ID");
        if((resend==null||from==null)&&(waToken==null||waPhoneId==null))return;
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(
          "SELECT e.id,e.usuario_id,e.canal,e.tentativas,n.titulo,n.mensagem,u.email,COALESCE(pr.email_ativo,TRUE) email_ativo,COALESCE(pr.whatsapp_ativo,FALSE) whatsapp_ativo,pr.whatsapp_numero " +
          "FROM notificacao_entregas e JOIN notificacoes n ON n.id=e.notificacao_id JOIN usuarios u ON u.id=e.usuario_id " +
          "LEFT JOIN preferencias_notificacao pr ON pr.usuario_id=e.usuario_id " +
          "WHERE e.status='PENDENTE' AND e.proxima_tentativa_em<=NOW() ORDER BY e.id LIMIT 25")){
            try(ResultSet r=p.executeQuery()){while(r.next()){
                long id=r.getLong("id"),uid=r.getLong("usuario_id");String channel=r.getString("canal"),title=r.getString("titulo"),message=r.getString("mensagem");
                boolean enabled="EMAIL".equals(channel)?r.getBoolean("email_ativo"):r.getBoolean("whatsapp_ativo");
                String destination="EMAIL".equals(channel)?r.getString("email"):r.getString("whatsapp_numero");
                boolean configured="EMAIL".equals(channel)?resend!=null&&from!=null:waToken!=null&&waPhoneId!=null;
                if(!enabled||destination==null||destination.isBlank()){
                    update(id,"AGUARDANDO_CONFIGURACAO",0,"Canal desativado ou destinatário não cadastrado.");continue;
                }
                if(!configured)continue;
                try{
                    HttpRequest request="EMAIL".equals(channel)?emailRequest(resend,from,destination,title,message):whatsAppRequest(waToken,waPhoneId,destination,message);
                    HttpResponse<String> response=HTTP.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if(response.statusCode()>=200&&response.statusCode()<300)update(id,"ENVIADA",1,"Entrega aceita pelo provedor.");
                    else fail(id,r.getInt("tentativas"),"Provedor retornou HTTP "+response.statusCode()+": "+limit(response.body(),300));
                }catch(Exception ex){fail(id,r.getInt("tentativas"),limit(ex.getMessage(),300));}
            }}
        }catch(Exception ex){System.err.println("LaunchControl: falha ao processar notificações externas: "+ex.getClass().getSimpleName());}
    }
    private static HttpRequest emailRequest(String token,String from,String to,String subject,String body){
        String payload="{\"from\":"+Json.quote(from)+",\"to\":["+Json.quote(to)+"],\"subject\":"+Json.quote(subject)+",\"text\":"+Json.quote(body)+"}";
        return HttpRequest.newBuilder(URI.create("https://api.resend.com/emails")).timeout(Duration.ofSeconds(15))
          .header("Authorization","Bearer "+token).header("Content-Type","application/json")
          .POST(HttpRequest.BodyPublishers.ofString(payload,StandardCharsets.UTF_8)).build();
    }
    private static HttpRequest whatsAppRequest(String token,String phoneId,String to,String body){
        String number=to.replaceAll("[^0-9]","");
        String payload="{\"messaging_product\":\"whatsapp\",\"recipient_type\":\"individual\",\"to\":"+Json.quote(number)+",\"type\":\"text\",\"text\":{\"preview_url\":false,\"body\":"+Json.quote(body)+"}}";
        String version=env("WHATSAPP_API_VERSION");if(version==null)version="v21.0";
        return HttpRequest.newBuilder(URI.create("https://graph.facebook.com/"+version+"/"+phoneId+"/messages")).timeout(Duration.ofSeconds(15))
          .header("Authorization","Bearer "+token).header("Content-Type","application/json")
          .POST(HttpRequest.BodyPublishers.ofString(payload,StandardCharsets.UTF_8)).build();
    }
    private static void fail(long id,int attempts,String details){int next=attempts+1;String status=next>=5?"FALHA":"PENDENTE";long delay=Math.min(360,1L<<Math.min(next,8));
        update(id,status,1,limit(details,500),delay);
    }
    private static void update(long id,String status,int increment,String details){update(id,status,increment,details,0);}
    private static void update(long id,String status,int increment,String details,long delayHours){
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(
          "UPDATE notificacao_entregas SET status=?,tentativas=tentativas+?,ultima_resposta=?,enviada_em=CASE WHEN ?='ENVIADA' THEN NOW() ELSE enviada_em END,proxima_tentativa_em=DATE_ADD(NOW(),INTERVAL ? HOUR) WHERE id=?")){
            p.setString(1,status);p.setInt(2,increment);p.setString(3,details);p.setString(4,status);p.setLong(5,delayHours);p.setLong(6,id);p.executeUpdate();
        }catch(SQLException e){System.err.println("LaunchControl: não foi possível atualizar entrega #"+id);}
    }
    private static String env(String name){String value=System.getenv(name);return value==null||value.isBlank()?null:value.trim();}
    private static String limit(String value,int max){if(value==null)return "Sem detalhes do provedor.";String clean=value.replaceAll("[\\r\\n]"," ");return clean.length()>max?clean.substring(0,max):clean;}
}
