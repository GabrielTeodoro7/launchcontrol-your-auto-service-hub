package br.com.launchcontrol;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Importa até 500 referências CSV; valida tudo antes de persistir a transação. */
@WebServlet("/api/compatibilidade-pecas/importacao")
public class FitmentImportServlet extends HttpServlet {
    private static final int MAX_ROWS = 500;
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res)throws IOException{
        req.setCharacterEncoding("UTF-8");HttpSession s=req.getSession(false);
        if(s==null||!"ADMINISTRADOR".equals(s.getAttribute("userRole"))){Json.error(res,403,"Somente administradores podem importar compatibilidades.");return;}
        String csv=req.getParameter("csv");
        if(csv==null||csv.isBlank()||csv.length()>500_000){Json.error(res,400,"Informe um CSV de até 500 KB.");return;}
        List<List<String>> rows;
        try{rows=parse(csv);if(rows.size()<2||rows.size()>MAX_ROWS+1)throw new IllegalArgumentException("O CSV deve conter cabeçalho e até 500 linhas.");}
        catch(IllegalArgumentException e){Json.error(res,400,e.getMessage());return;}
        List<String> expected=List.of("pecaId","marcaVeiculo","modeloVeiculo","anoInicio","anoFim","motorizacao","codigoOem","status","fonte","referencia","observacoes");
        if(!normalize(rows.get(0)).equals(expected)){Json.error(res,400,"Cabeçalho inválido. Use exatamente: "+String.join(",",expected));return;}
        long admin=(Long)s.getAttribute("userId");
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                List<String> errors=new ArrayList<>();
                String checkSql="SELECT id FROM pecas WHERE id=? AND ativo=TRUE";
                String insertSql="INSERT INTO compatibilidade_pecas(peca_id,marca_veiculo,modelo_veiculo,ano_inicio,ano_fim,motorizacao,codigo_oem,status,fonte,referencia,observacoes,verificado_por,verificado_em) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
                try(PreparedStatement check=c.prepareStatement(checkSql);PreparedStatement insert=c.prepareStatement(insertSql)){
                    for(int i=1;i<rows.size();i++){
                        List<String> row=rows.get(i);int line=i+1;
                        try{
                            if(row.size()!=expected.size())throw new IllegalArgumentException("quantidade de colunas inválida");
                            long part=Long.parseLong(row.get(0).trim());String make=required(row.get(1),80),model=required(row.get(2),100);
                            Integer from=year(row.get(3)),to=year(row.get(4));String engine=optional(row.get(5),80),oem=optional(row.get(6),80);
                            String status=required(row.get(7),30).toUpperCase(Locale.ROOT),source=optional(row.get(8),180),reference=optional(row.get(9),500),notes=optional(row.get(10),500);
                            if(from!=null&&to!=null&&to<from)throw new IllegalArgumentException("ano final anterior ao inicial");
                            if(!Set.of("CONFIRMADA","INCOMPATIVEL","NAO_VERIFICADA").contains(status))throw new IllegalArgumentException("status inválido");
                            boolean verified="CONFIRMADA".equals(status)||"INCOMPATIVEL".equals(status);
                            if(verified&&(source==null||reference==null))throw new IllegalArgumentException("status verificado exige fonte e referência");
                            check.setLong(1,part);try(ResultSet found=check.executeQuery()){if(!found.next())throw new IllegalArgumentException("peça ativa não encontrada");}
                            insert.setLong(1,part);insert.setString(2,make);insert.setString(3,model);
                            nullableYear(insert,4,from);nullableYear(insert,5,to);insert.setString(6,engine);insert.setString(7,oem);insert.setString(8,status);
                            insert.setString(9,source);insert.setString(10,reference);insert.setString(11,notes);
                            if(verified){insert.setLong(12,admin);insert.setTimestamp(13,Timestamp.from(Instant.now()));}
                            else{insert.setNull(12,Types.BIGINT);insert.setNull(13,Types.TIMESTAMP);}
                            insert.addBatch();
                        }catch(Exception e){errors.add("Linha "+line+": "+safe(e.getMessage()));}
                    }
                    if(!errors.isEmpty()){c.rollback();Json.error(res,400,String.join(" | ",errors.subList(0,Math.min(errors.size(),20))));return;}
                    int[] results=insert.executeBatch();
                    try(PreparedStatement audit=c.prepareStatement("INSERT INTO auditoria(usuario_id,acao,entidade,entidade_id,detalhes) VALUES(?,?,?,?,?)")){
                        audit.setLong(1,admin);audit.setString(2,"IMPORTAR_COMPATIBILIDADE");audit.setString(3,"compatibilidade_pecas");audit.setNull(4,Types.BIGINT);audit.setString(5,"Importação CSV: "+results.length+" linhas");audit.executeUpdate();
                    }
                    c.commit();res.setStatus(201);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"ok\":true,\"importadas\":"+results.length+"}");
                }
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
        }catch(SQLException e){Json.error(res,500,"Importação não concluída; nenhuma linha foi confirmada.");}
    }
    private static List<List<String>> parse(String text){
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false;
        for(int i=0;i<text.length();i++){char ch=text.charAt(i);
            if(ch=='"'&&quoted&&i+1<text.length()&&text.charAt(i+1)=='"'){field.append('"');i++;}
            else if(ch=='"'){quoted=!quoted;}
            else if(ch==','&&!quoted){row.add(field.toString().trim());field.setLength(0);}
            else if((ch=='\n'||ch=='\r')&&!quoted){if(ch=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;row.add(field.toString().trim());field.setLength(0);if(row.stream().anyMatch(v->!v.isBlank()))rows.add(row);row=new ArrayList<>();}
            else field.append(ch);
        }
        if(quoted)throw new IllegalArgumentException("CSV contém aspas não fechadas.");
        row.add(field.toString().trim());if(row.stream().anyMatch(v->!v.isBlank()))rows.add(row);return rows;
    }
    private static List<String> normalize(List<String> row){List<String> out=new ArrayList<>();for(String v:row)out.add(v.trim().replace("\uFEFF",""));return out;}
    private static String required(String v,int max){String x=optional(v,max);if(x==null)throw new IllegalArgumentException("campo obrigatório ausente");return x;}
    private static String optional(String v,int max){if(v==null||v.isBlank())return null;String x=v.trim();if(x.length()>max)throw new IllegalArgumentException("campo excede "+max+" caracteres");return x;}
    private static Integer year(String v){if(v==null||v.isBlank())return null;int y=Integer.parseInt(v.trim());if(y<1950||y>2100)throw new IllegalArgumentException("ano fora do intervalo 1950–2100");return y;}
    private static void nullableYear(PreparedStatement p,int i,Integer v)throws SQLException{if(v==null)p.setNull(i,Types.SMALLINT);else p.setInt(i,v);}
    private static String safe(String s){return s==null||s.isBlank()?"dados inválidos":s.replaceAll("[\r\n]"," ");}
}
