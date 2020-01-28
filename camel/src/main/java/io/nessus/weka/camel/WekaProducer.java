/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nessus.weka.camel;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.support.DefaultProducer;

import io.nessus.weka.AssertState;
import io.nessus.weka.camel.WekaConfiguration.Command;
import io.nessus.weka.utils.DatasetUtils;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.CSVLoader;
import weka.core.converters.ConverterUtils.DataSink;
import weka.core.converters.ConverterUtils.DataSource;
import weka.core.converters.Loader;

public class WekaProducer extends DefaultProducer {

    public WekaProducer(WekaEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WekaEndpoint getEndpoint() {
        return (WekaEndpoint)super.getEndpoint();
    }

    public WekaConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        WekaEndpoint endpoint = getEndpoint();
        Command cmd = endpoint.getCommand();
        
        if (Command.version == cmd) {

            Message msg = exchange.getMessage();
            msg.setBody(endpoint.wekaVersion());

        } else if (Command.read == cmd) {

            Message msg = exchange.getMessage();
            msg.setBody(handleReadCmd(exchange));

        } else if (Command.write == cmd) {

            Message msg = exchange.getMessage();
            msg.setBody(handleWriteCmd(exchange));

        } else if (Command.filter == cmd) {

            Message msg = exchange.getMessage();
            msg.setBody(handleFilterCmd(exchange));
            
        }
    }

    private Instances handleReadCmd(Exchange exchange) throws Exception {
        
        Instances instances = assertInstancesBody(exchange);
        return instances;
    }

    private Object handleWriteCmd(Exchange exchange) throws Exception {
        
        Instances instances = assertInstancesBody(exchange);
        String outpath = getConfiguration().getOutPath();
        
        if (outpath != null) {
            
            File outFile = Paths.get(outpath).toFile();
            outFile.getParentFile().mkdirs();
            DataSink.write(outpath, instances);
            return instances;
            
        } else {
            
            // The internal implementation of DataSink does this.. 
            // Instances.toString().getBytes()
            //
            // Therefore, we avoid creating yet another copy of the
            // instance data and call Instances.toString() as well 
            byte[] bytes = instances.toString().getBytes();
            return new ByteArrayInputStream(bytes);
        }
    }

    private Instances handleFilterCmd(Exchange exchange) throws Exception {
        
        String name = getConfiguration().getName();
        String options = getConfiguration().getOptions();
        AssertState.notNull(name, "Cannot obtain filter name from: " + getEndpoint().getEndpointUri());
        Instances instances = assertInstancesBody(exchange);
        
        instances = DatasetUtils.applyFilter(instances, new DatasetUtils.OperatorSpec(name, options));
        return instances;
    }

    private Instances assertInstancesBody(Exchange exchange) throws Exception {
        
        Message msg = exchange.getMessage();
        Object body = msg.getBody();
        
        Instances instances = msg.getBody(Instances.class);
        
        if (instances == null) {
            
            if (body instanceof GenericFile) {
                
                GenericFile<?> file = (GenericFile<?>) body;
                AssertState.isFalse(file.isDirectory(), "Directory not supported: " + file);
                String absolutePath = file.getAbsoluteFilePath();
                DataSource source = new DataSource(absolutePath); 
                instances = source.getDataSet();
                
            } else if (body instanceof URL) {
                
                URL url = (URL) body;
                DataSource source = new DataSource(url.toExternalForm()); 
                instances = source.getDataSet();
                
            } else if (body instanceof InputStream) {
                
                InputStream input = (InputStream) body;
                if (input.markSupported()) {
                    input.mark(256);
                }
                
                // First try .arff
                try {
                    Loader loader = new ArffLoader();
                    loader.setSource(input);
                    loader.getStructure();
                    instances = loader.getDataSet();
                } catch (IOException ex) {
                    String exmsg = ex.getMessage();
                    if (!exmsg.contains("Unable to determine structure as arff")) {
                        throw ex;
                    }
                    if (input.markSupported()) {
                        input.reset();
                    }
                }
                
                // Next try .csv
                if (instances == null) {
                    Loader loader = new CSVLoader();
                    loader.setSource(input);
                    loader.getStructure();
                    instances = loader.getDataSet();
                }
            }
        }
        
        AssertState.notNull(instances, "Cannot obtain instances from body: " + body);
        return instances;
    }
}
