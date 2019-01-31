package nl.knmi.geoweb.aviation.tacimport.taf;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;

import fi.fmi.avi.converter.AviMessageConverter;
import fi.fmi.avi.converter.ConversionIssue;
import fi.fmi.avi.converter.ConversionResult;
import fi.fmi.avi.converter.tac.conf.TACConverter;
import fi.fmi.avi.model.taf.TAF;
import fi.fmi.avi.model.taf.immutable.TAFImpl;
import nl.knmi.adaguc.tools.Debug;
import nl.knmi.adaguc.tools.Tools;
import nl.knmi.geoweb.backend.product.taf.Taf;
import nl.knmi.geoweb.backend.product.taf.TafValidationResult;
import nl.knmi.geoweb.backend.product.taf.TafValidator;
import nl.knmi.geoweb.backend.product.taf.converter.TafConverter;
import nl.knmi.geoweb.iwxxm_2_1.converter.conf.GeoWebConverterConfig;

@Controller
public class TafImporter {
    public TafImporter() {
        System.err.println("Creating TafImporter");
    }

    @Autowired
    private AviMessageConverter converter;

    @Autowired
    ObjectMapper tafObjectMapper;

    @Autowired
    TafValidator tafValidator;

    public TAF importFile(String fn) {
        System.err.println("Importing "+fn);
        try (BufferedReader br = Files.newBufferedReader(Paths.get(fn))){
            String dt_s=br.readLine();
            String header=br.readLine();
            String[] tacLines=br.lines().
                    collect(Collectors.toList()).toArray(new String[0]);
            String tac=String.join("\n", tacLines);

            ZonedDateTime issueTime=ZonedDateTime.parse(dt_s+"+0100", DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ssx"));
            parseTaf(tac, issueTime, fn);
        } catch (IOException e) {

        }
        return null;
    }

    public void importTAFs(String srcdir, String fileMask, String destdir, String reportSelector, String mwoSelector) {

        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:"+fileMask);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(srcdir), fileMask)) {
            List<Path> files=new ArrayList<>();
            stream.forEach(files::add);
            files.sort(Comparator.comparing(Path::toString));
            for (Path entry : files) {
//                System.err.println(entry.getParent() + entry.getFileSystem().getSeparator() + entry.getFileName());
                handleCollectFile(entry.getParent() + entry.getFileSystem().getSeparator() + entry.getFileName(), destdir, reportSelector, mwoSelector);
            }

        } catch (IOException ex) {
        }

    }

    public void handleCollectFile(String fn, String destDir, String reportSelector, String mwoSelector ){
        try (BufferedReader br = Files.newBufferedReader(Paths.get(fn))){
            String dt_s;
            while ((dt_s = br.readLine())!=null){

                String header = br.readLine();
                String[]terms=header.split(" ");

                String line;
                StringBuilder sb=new StringBuilder();
                List<String> linesList = new ArrayList<String>();
                while ((line=br.readLine()).trim().length()>0) {
                    sb.append(line);
                    sb.append('\n');
                    linesList.add(line);
                }

                if (terms[0].equals(reportSelector)&&(terms[1].equals(mwoSelector))){
                    //Write sb to TAC file in destdir
                    ZonedDateTime issueTime=ZonedDateTime.parse(dt_s+"+0100", DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ssx"));
                    String dateStamp=issueTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                    //                    String collectFileBaseName=destdir+Paths.get(fn).getFileSystem().getSeparator()+"TAFCollected_"+terms[1]+"_"+dateStamp;
                    //                    System.err.println(collectFileBaseName+"\n"+sb.toString());
                    String[]lines=linesList.toArray(new String[0]);
                    boolean firstLine=true;
                    int cnt=0;
                    StringBuilder tafBuilder=new StringBuilder();
                    String airport=null;
                    boolean nilTaf=false;
                    while (cnt<lines.length){
                        if (firstLine) {
                            String[] words=lines[cnt].split(" ");
                            if (words[1].equals("COR")||words[1].equals("AMD")||words[1].equals("RTD")||words[1].equals("CNL")) {
                                airport = words[2];
                            }else {
                                airport=words[1];
                            }
                            firstLine=false;
                            nilTaf=lines[cnt].endsWith("NIL=");
                        }
                        tafBuilder.append(lines[cnt]);
                        tafBuilder.append("\n");

                        if (lines[cnt].endsWith("=")&&!nilTaf) {
                            String fileBaseName = destDir + Paths.get(fn).getFileSystem().getSeparator() + "TAF_" + airport + "_" + dateStamp;
                            System.err.println(fileBaseName + ":");
                            String tafStr=tafBuilder.toString();
                            tafStr=tafStr.substring(0,tafStr.length()-1); //Chop off last \n
//                            System.err.println(">"+tafStr);
//                            System.err.println("\n");
                            String tafFn=fileBaseName+".tac";
                            Tools.writeFile(tafFn, tafStr);
                            parseTaf(tafFn, issueTime,destDir);

                            tafBuilder=new StringBuilder();
                            firstLine=true;
                        }
                        cnt++;
                    }
                }

            }

        } catch (IOException e) {

        }
    }

    @Autowired
    private TafConverter tafConverter;


    public TAF parseTaf(String tafFn, ZonedDateTime t, String path){

        String taf= null;
        try {
            taf = Tools.readFile(tafFn);
        } catch (IOException e) {
            e.printStackTrace();
        }

        TAF retval=null;

        List<String> logInfo=new ArrayList<String>();

        Debug.println("parseTaf("+t+", "+tafFn+","+taf+")");
        ConversionResult<TAFImpl> result = converter.convertMessage(taf, TACConverter.TAC_TO_IMMUTABLE_TAF_POJO);
        if ((ConversionResult.Status.SUCCESS != result.getStatus())&& (ConversionResult.Status.WITH_WARNINGS != result.getStatus())) {
            //Conversion to immutable TAF failed
            Debug.println("Failed to convert "+tafFn+" to FMITaf");
            logInfo.add("Failed to convert "+tafFn+" to FMITaf");
            for (ConversionIssue issue:result.getConversionIssues()) {
                Debug.println(issue.toString());
                logInfo.add(issue.toString());
            }
        } else {
            Optional<TAFImpl> pojo = result.getConvertedMessage();
            TAFImpl correctedTaf=TAFImpl.Builder.from(pojo.get()).withAllTimesComplete(t).build();
            ConversionResult<Taf> geoWebTafResult=converter.convertMessage(correctedTaf, GeoWebConverterConfig.TAF_TO_GEOWEBTAF_POJO);
            if ((ConversionResult.Status.SUCCESS != geoWebTafResult.getStatus())&&(ConversionResult.Status.WITH_WARNINGS != geoWebTafResult.getStatus())) {
                //Conversion to GeoWebTaf failed
                Debug.println("Failed to convert FMITaf ("+tafFn+") to GeoWebTaf");
                logInfo.add("Failed to convert FMITaf ("+tafFn+") to GeoWebTaf");
                for (ConversionIssue issue:geoWebTafResult.getConversionIssues()) {
                    Debug.println(issue.toString());
                    logInfo.add(issue.toString());
                }
            } else {
                Taf geoWebTaf=geoWebTafResult.getConvertedMessage().get();
                String back2TAC=geoWebTaf.toTAC();
                //                Debug.println("and back to TAF in TAC:"+back2TAC);
                //TODO Compare taf and back2TAC in smart way
                String cleanedSourceTaf = taf.replaceAll("\\s+", " ").replace("=","");
                String cleanedResultTaf = back2TAC.replaceAll("\\s+", " ");
                if (!cleanedResultTaf.equals(cleanedSourceTaf)) {
                    Debug.println("Reconstituted TAF differs from source");
                    Debug.println("  "+cleanedSourceTaf+"\n  "+cleanedResultTaf);
                    logInfo.add("Reconstituted TAF differs from source");
                    logInfo.add("  "+cleanedSourceTaf+"\n  "+cleanedResultTaf);
                    try {
                        Tools.writeFile(tafFn.replace("TAF_", "GEOWEBTAF_"), back2TAC);
                    } catch (IOException e) {
                        Debug.println("Can't write file "+tafFn.replace("TAF_", "GEOWEBTAF_"));
                    }
                } else {

                    try {
                        String geoWebTafStr = geoWebTaf.toJSON(tafObjectMapper);
                        //                Debug.println("geowebTaf:"+geoWebTafStr);
                        JSONObject jsonResult = verifyTAF(geoWebTafStr);
                        if ((jsonResult.get("succeeded") != null) && jsonResult.get("succeeded").equals(false)) {
                            Debug.println("verification failed for file " + tafFn);
                            logInfo.add("verification failed for file " + tafFn);
                            JSONObject errors = jsonResult.getJSONObject("errors");
                            Debug.println(errors.toString());
                            logInfo.add(errors.toString());
                        } else {
                            String iwxxmResult = tafConverter.ToIWXXM_2_1(geoWebTaf);
                            if (iwxxmResult.equals("FAIL")) {
                                Debug.println("IWXXM Conversion failed!!!"); //Save no xml
                                logInfo.add("IWXXM Conversion failed for "+tafFn);
                            } else {
                                try {
                                    String time = correctedTaf.getIssueTime().getCompleteTime().get().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                                    String iwxxmName = "A_" + "LTNL99" + geoWebTaf.metadata.getLocation() + geoWebTaf.getMetadata().getValidityStart().format(DateTimeFormatter.ofPattern("ddHHmm"));
                                    switch (geoWebTaf.getMetadata().getType()) {
                                        case amendment:
                                            iwxxmName += "AMD";
                                            break;
                                        case correction:
                                            iwxxmName += "COR";
                                            break;
                                        case canceled:
                                            iwxxmName += "CNL";
                                            break;
                                        default:
                                            break;
                                    }

                                    iwxxmName += "_C_" + geoWebTaf.metadata.getLocation() + "_" + time;
                                    Tools.writeFile(path + "/" + iwxxmName + ".xml", iwxxmResult);
                                } catch (IOException e) {
                                    Debug.println("writing of IWXXM file for "+tafFn+" failed: ");
                                    Debug.printStackTrace(e);
                                }
                            }
                        }
                    } catch (IOException e) {
                        Debug.printStackTrace(e);
                    } catch (ParseException e) {
                        Debug.printStackTrace(e);
                    }

                    retval=pojo.get();
                }
            }
        }
        if (logInfo.size()>0) {
            String logFn=tafFn.replace("tac", "tac.LOG");
            try {
                Tools.writeFile(logFn, taf+"\n"+String.join("\n", logInfo)+"\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return retval;
    }

    public JSONObject verifyTAF(String tafStr) throws IOException, JSONException, ParseException {
        //       tafStr = URLDecoder.decode(tafStr, "UTF8");
        /* Add TAC */
        String TAC = "unable to create TAC";
        try {
            TAC = tafObjectMapper.readValue(tafStr, Taf.class).toTAC();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        try {
            TafValidationResult jsonValidation = tafValidator.validate(tafStr);
            if (jsonValidation.isSucceeded() == false) {
                ObjectNode errors = jsonValidation.getErrors();
                System.err.println("/tafs/verify: TAF validation failed");
                JSONObject finalJson = new JSONObject()
                        .put("succeeded", false)
                        .put("errors", new JSONObject(errors.toString()))
                        .put("TAC", TAC)
                        .put("message", "TAF is not valid");
                return finalJson;
            } else {
                JSONObject json = new JSONObject().put("succeeded", true).put("message", "TAF is verified.").put("TAC", TAC);
                return json;
            }
        } catch (ProcessingException e) {
            e.printStackTrace(System.err);
            JSONObject json = new JSONObject().
                    put("succeeded", false).put("message", "Unable to validate taf");
            return json;
        }
    }
}
