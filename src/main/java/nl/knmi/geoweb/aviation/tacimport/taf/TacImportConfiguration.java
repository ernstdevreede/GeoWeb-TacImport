package nl.knmi.geoweb.aviation.tacimport.taf;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import fi.fmi.avi.converter.AviMessageConverter;
import fi.fmi.avi.converter.AviMessageSpecificConverter;
import fi.fmi.avi.converter.iwxxm.conf.IWXXMConverter;
import fi.fmi.avi.converter.tac.conf.TACConverter;
import fi.fmi.avi.model.taf.TAF;
import fi.fmi.avi.model.taf.immutable.TAFImpl;
import nl.knmi.adaguc.tools.Debug;
import nl.knmi.geoweb.backend.aviation.AirportStore;
import nl.knmi.geoweb.backend.product.taf.Taf;
import nl.knmi.geoweb.backend.product.taf.TafSchemaStore;
import nl.knmi.geoweb.backend.product.taf.TafValidator;
import nl.knmi.geoweb.backend.product.taf.converter.TafConverter;
import nl.knmi.geoweb.iwxxm_2_1.converter.GeoWebTAFConverter;
import nl.knmi.geoweb.iwxxm_2_1.converter.GeoWebTafInConverter;
import nl.knmi.geoweb.iwxxm_2_1.converter.conf.GeoWebConverterConfig;

@Configuration
//@Import({TACConverter.class, GeoWebTafInConverter.class, IWXXMConverter.class, GeoWebTAFConverter.class, GeoWebConverterConfig.class})
@Import({TACConverter.class, IWXXMConverter.class, GeoWebConverterConfig.class})
public class TacImportConfiguration {
    @Autowired
    private AviMessageSpecificConverter<String, TAF> tafTACParser;

    @Autowired
    private AviMessageSpecificConverter<String, TAFImpl> immutableTafTACParser;

    @Autowired
    private AviMessageSpecificConverter<TAF, Taf> geoWebTafImporter;

    @Bean
    public AviMessageConverter aviMessageConverter() {
        AviMessageConverter p = new AviMessageConverter();

        p.setMessageSpecificConverter(TACConverter.TAC_TO_TAF_POJO, tafTACParser);
        p.setMessageSpecificConverter(TACConverter.TAC_TO_IMMUTABLE_TAF_POJO, immutableTafTACParser);
        p.setMessageSpecificConverter(GeoWebConverterConfig.TAF_TO_GEOWEBTAF_POJO, geoWebTafImporter);
        return p;
    }

    public static final String DATEFORMAT_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    @Bean("tafObjectMapper")
    public static ObjectMapper getTafObjectMapperBean() {
        Debug.println("Init TafObjectMapperBean (TacImportConfiguration)");
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.setTimeZone(TimeZone.getTimeZone("UTC"));
        om.setDateFormat(new SimpleDateFormat(DATEFORMAT_ISO8601));
        om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        om.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        return om;
    }

    @Bean("geoWebObjectMapper")
    public static ObjectMapper getGeoWebObjectMapperBean() {
        Debug.println("Init GeoWebObjectMapperBean (TacImportConfiguration)");
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.setTimeZone(TimeZone.getTimeZone("UTC"));
        om.setDateFormat(new SimpleDateFormat(DATEFORMAT_ISO8601));
        om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        om.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        return om;
    }

    private static String productstorelocation="/tmp/archivedtafs";

    @Bean
    public TafValidator getTafValidator() throws IOException {
        TafSchemaStore tafSchemaStore = new TafSchemaStore(productstorelocation);
        return new TafValidator(tafSchemaStore, getTafObjectMapperBean());
    }

    private TafConverter tafConverter;
    @Bean
    public TafConverter getTafConverter() {
        if (tafConverter==null) {
            tafConverter = new TafConverter();
        }
        return tafConverter;
    }

    private AirportStore airportStore;

/*
    @Autowired
    private ObjectMapper jacksonObjectMapper;
*/

    @Bean
    public AirportStore getAirportStore() throws IOException {
        if (airportStore==null) {
            airportStore = new AirportStore(productstorelocation);
        }
        return airportStore;
    }
}
