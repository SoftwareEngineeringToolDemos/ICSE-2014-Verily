package core.filters;

import content.TemplateFactory;
import core.Verily;
import core.VerilyEnv;
import core.VerilyFilter;
import freemarker.template.Template;
import org.apache.commons.lang.StringUtils;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import verily.lang.VerilyTable;
import verily.lang.VerilyType;
import verily.lang.util.MRRTableSet;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static core.VerilyFilter.VerilyFilterAction.*;
import static core.Constants.*;
import static core.ResponseUtils.*;


/**
 * Author: John L. Singleton <jsinglet@gmail.com>
 */
public class AjaxHarnessFilter extends VerilyFilter {



    @Override
    public VerilyFilterAction handleRequest(Request request, Response response, VerilyEnv env, VerilyFilterAction lastFilterResult) {

        if(request.getPath().toString().equals("/_verilyApp.js")) {

            //TODO - this should probably be cached?
            sendAjaxHarness(request, response, env);

            return getFilterResponse(STOP, HTTP_OK);
        }

        return OK;
    }



    public void sendAjaxHarness(Request request, Response response, VerilyEnv env) {
        try {

            Writer body = new OutputStreamWriter(response.getOutputStream());

            long time = System.currentTimeMillis();

            response.setValue(CONTENT_TYPE, "text/javascript");
            response.setValue(SERVER, SERVER_NAME);
            response.setDate(DATE, time);
            response.setDate(LAST_MODIFIED, time);
            response.setCode(HTTP_OK);

            try {

                Map vars = new HashMap();

                List modules = new ArrayList();

                VerilyTable table = env.getTranslationTable().getMethodTable();

                for (String module : table.getTable().keySet()) {

                    Map m = new HashMap();

                    m.put("name", module);


                    List functions = new ArrayList();

                    // add the functions

                    for (String f : table.getTable().get(module).keySet()) {

                        Map fParams = new HashMap();

                        List<VerilyType> formalParams = table.getTable().get(module).get(f).getFormalParameters();

                        List<String> paramNames = new ArrayList<String>();

                        for (VerilyType t : formalParams) {
                            paramNames.add(t.getName());
                        }

                        fParams.put("name", f);
                        fParams.put("quotedArgList", "\"" + StringUtils.join(paramNames, "\", \"") + "\"");
                        fParams.put("argList", StringUtils.join(paramNames, ", "));
                        fParams.put("asyncArgList", paramNames.size());





                        functions.add(fParams);

                    }


                    m.put("functions", functions);


                    modules.add(m);

                }


                vars.put("version", Verily.VERSION);
                vars.put("modules", modules);

                Template t = TemplateFactory.getInstance().getAjaxTemplate();

                t.process(vars, body);
            } catch (IOException e) {
                logger.error("Error during render of Ajax template: {}", e.getMessage());
                body.write("Sorry, but the endpoint you requested does not exist.");
            } finally {
                body.close();
            }


        } catch (Exception e) { // this is horribly fatal.
            logger.error(e.getMessage());
            e.printStackTrace();
        }
    }
}