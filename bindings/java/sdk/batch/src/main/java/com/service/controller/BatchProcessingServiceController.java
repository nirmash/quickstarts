package com.service.controller;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.ClassPathResource;


@RestController
public class BatchProcessingServiceController {
    
    private static final Logger logger = LoggerFactory.getLogger(BatchProcessingServiceController.class);
    private static final String cronBindingPath = "/cron";
    private static final String sqlBindingName = "sqldb";
    
    @PostMapping(path = cronBindingPath, consumes = MediaType.ALL_VALUE)
    public ResponseEntity<String> processBatch() throws IOException, Exception {
        
        logger.info("Processing batch..");

        Orders ordList = this.loadOrdersFromFile("orders.json");

        try (DaprClient client = new DaprClientBuilder().build()) {

            for (Order order : ordList.orders) {
                String sqlText = String.format(
                    "insert into orders (orderid, customer, price) " +
                    "values (%s, '%s', %s);", 
                    order.orderid, order.customer, order.price);
                logger.info(sqlText);
    
                Map<String, String> metadata = new HashMap<String, String>();
                metadata.put("sql", sqlText);
 
                // Invoke sql output binding using Dapr SDK
                client.invokeBinding(sqlBindingName, "exec", null, metadata).block();
            } 

            logger.info("Finished processing batch");

            return ResponseEntity.ok("Finished processing batch");

        } catch (Exception e) {
            logger.error("Dapr client failed:", e);
            throw e;
        }

    }

    private Orders loadOrdersFromFile(String path) throws Exception {
        // this is a mock to get things running temporarily

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);


        if (!(path == "")) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                Orders obj = mapper.readValue(is, Orders.class);
                return obj;
            } catch (Exception e) {
                logger.error(e.toString());
                throw e;
            }
        } else {
            String json = String.join(System.getProperty("line.separator"), 
            "{\"orders\": [",
            "{\"orderid\": 1, \"customer\": \"John Smith\", \"price\": 100.32},",
            "{\"orderid\": 2, \"customer\": \"Jane Bond\", \"price\": 15.4},",
            "{\"orderid\": 3, \"customer\": \"Tony James\", \"price\": 35.56}",
            "]",
            "}");

            return mapper.readValue(json, Orders.class);
        }

    }
}
            
@Getter
@Setter
class Order {
    public int orderid;
    public String customer;
    public float price;
}

@Getter
@Setter
class Orders {
    public List<Order> orders;
}
