package io.nessus.weka.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

import io.nessus.weka.AssertArg;
import io.nessus.weka.Dataset;
import io.nessus.weka.NominalPredictor;
import io.nessus.weka.testing.AbstractWekaTest;
import weka.core.Instances;

public class PredictionTest extends AbstractWekaTest {
    
    @Test
    public void testJ48() throws Exception {
        
        try (CamelContext camelctx = new DefaultCamelContext()) {
            
            camelctx.addRoutes(new RouteBuilder() {
                
                @Override
                public void configure() throws Exception {
                    
                    // Use weka to read the data file
                    from("weka:read?path=src/test/resources/data/sfny-test.arff")
                    
                    // Push these instances for later use
                    .to("weka:push?dsname=sfny-test")
                    
                    // Remove the class attribute 
                    .to("weka:filter?apply=Remove -R last")
                    
                    // Add the 'prediction' placeholder attribute 
                    .to("weka:filter?apply=Add -N predicted -T NOM -L 0,1")
                    
                    // Rename the relation 
                    .to("weka:filter?apply=RenameRelation -modify sfny-predicted")
                    
                    // Load an already existing model
                    .to("weka:model?loadFrom=src/test/resources/data/sfny-j48.model")
                    
                    // Use a processor to do the prediction
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            Dataset dataset = exchange.getMessage().getBody(Dataset.class);
                            dataset.applyToInstances(new NominalPredictor());
                        }
                    })
                    
                    // Write the data file
                    .to("weka:write?path=src/test/resources/data/sfny-predicted.arff")
                    
                    .to("direct:end");
                }
            });
            camelctx.start();
            
            ConsumerTemplate consumer = camelctx.createConsumerTemplate();
            Dataset dataset = consumer.receiveBody("direct:end", Dataset.class);
            
            Instances wasdata = dataset.getInstances();
            Instances expdata = dataset.pop("sfny-test").getInstances();
            int numInstances = expdata.numInstances();
            
            int correct = numCorrectlyClassified(expdata, wasdata);
            
            double accuracy = 100.0 * correct / numInstances;
            int incorrect = numInstances - correct;
            
            logInfo(String.format("Correctly Classified Instances   %d %.4f %%", correct, accuracy));
            logInfo(String.format("Incorrectly Classified Instances %d %.4f %%", incorrect, 100 - accuracy));
            
            Assert.assertEquals("88.8889", String.format("%.4f", accuracy));
        }
    }

    private int numCorrectlyClassified(Instances expdata, Instances wasdata) {
        AssertArg.isEqual(expdata.classIndex(), wasdata.classIndex());
        AssertArg.isEqual(expdata.size(), wasdata.size());
        int numInstances = expdata.numInstances();
        int clidx = expdata.classIndex();
        int correct = 0;
        for (int i = 0; i < numInstances; i++) {
            double expval = expdata.instance(i).value(clidx);
            double wasval = wasdata.instance(i).value(clidx);
            if (expval == wasval) correct += 1;
        }
        return correct;
    }
}