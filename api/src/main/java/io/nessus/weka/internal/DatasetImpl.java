/*-
 * #%L
 * Nessus :: Weka :: API
 * %%
 * Copyright (C) 2020 Nessus
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package io.nessus.weka.internal;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import io.nessus.common.AssertArg;
import io.nessus.common.AssertState;
import io.nessus.weka.Dataset;
import io.nessus.weka.FunctionalClassifier;
import io.nessus.weka.FunctionalEvaluation;
import io.nessus.weka.FunctionalInstances;
import io.nessus.weka.UncheckedException;
import io.nessus.weka.utils.DatasetUtils;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Attribute;
import weka.core.Instances;

public class DatasetImpl extends Dataset implements FunctionalEvaluation<Dataset>, FunctionalClassifier<Dataset>, FunctionalInstances<Dataset> {

    private static final String DEFAULT_SLOT = "_default";
    private static final String TRAINING_DATA_SLOT = "_training";
    private static final String TEST_DATA_SLOT = "_test";
    
    private final Map<String, Instances> storage = new HashMap<>();
    private Classifier classifier;
    private Evaluation evaluation;
    private Instances instances;

    public DatasetImpl(Instances instances) {
        this.instances = assignClassIndex(instances);
    }

    /**
     * Guess the class index if not set already
     */
    private Instances assignClassIndex(Instances instances) {
        AssertArg.notNull(instances, "Null instances");
        
        if (instances.classIndex() < 0) {
            Attribute attr = instances.attribute("class");
            if (attr != null) {
                instances.setClass(attr);
            } else {
                int numatts = instances.numAttributes();
                if (numatts > 0) {
                    attr = instances.attribute(numatts - 1);
                    if (attr.isNominal()) {
                        instances.setClass(attr);
                    }
                }
            }
        }
        return instances;
    }
    
    @Override
    public Dataset read(Path inpath) {
        Instances result = DatasetUtils.read(inpath);
        instances = assignClassIndex(result);
        return this;
    }
    
    @Override
    public Dataset read(String inpath) {
        Instances result = DatasetUtils.read(Paths.get(inpath));
        instances = assignClassIndex(result);
        return this;
    }
    
    @Override
    public Dataset read(URL url) {
        Instances result = DatasetUtils.read(url);
        instances = assignClassIndex(result);
        return this;
    }
    
    @Override
    public Dataset write(Path outpath) {
        DatasetUtils.write(instances, outpath);
        return this;
    }

    @Override
    public Dataset write(String outpath) {
        DatasetUtils.write(instances, Paths.get(outpath));
        return this;
    }

    @Override
    public Dataset push() {
        push(DEFAULT_SLOT);
        return this;
    }

    @Override
    public Dataset pushTrainingSet() {
        push(TRAINING_DATA_SLOT);
        return this;
    }

    @Override
    public Dataset pushTestSet() {
        push(TEST_DATA_SLOT);
        return this;
    }

    @Override
    public Dataset push(String name) {
        storage.put(name, new Instances(instances));
        return this;
    }

    @Override
    public Dataset pop() {
        pop(DEFAULT_SLOT);
        return this;
    }

    @Override
    public Dataset popTrainingSet() {
        pop(TRAINING_DATA_SLOT);
        return this;
    }

    @Override
    public Dataset popTestSet() {
        pop(TEST_DATA_SLOT);
        return this;
    }

    @Override
    public Dataset pop(String name) {
        Instances result = storage.remove(name);
        AssertState.notNull(result, "Cannot find instances with name '" + name + "' on stack");
        instances = assignClassIndex(result);
        return this;
    }

    @Override
    public Dataset apply(String filterSpec) {
        Instances result = DatasetUtils.applyFilter(getInstances(), filterSpec);
        instances = assignClassIndex(result);
        return this;
    }
    
    @Override
    public Dataset applyToInstances(UnaryOperator<Instances> operator) {
        Instances result = operator.apply(instances);
        instances = assignClassIndex(result);
        return this;
    }
    
    @Override
    public Dataset applyToInstances(Function<Dataset, Instances> function) {
        Instances result = function.apply(this);
        instances = assignClassIndex(result);
        return this;
    }

    @Override
    public Dataset consumeInstances(Consumer<Instances> consumer) {
        consumer.accept(instances);
        return this;
    }

    @Override
    public List<Attribute> getAttributes() {
        List<Attribute> result = new ArrayList<>();
        Enumeration<Attribute> en = instances.enumerateAttributes();
        while (en.hasMoreElements()) result.add(en.nextElement());
        return result;
    }

    @Override
    public Instances getInstances() {
        return instances;
    }

    @Override
    public Dataset applyToClassifier(Function<Dataset, Classifier> function) {
        Classifier result = function.apply(this);
        AssertState.notNull(result, "Null classifier");
        classifier = result;
        return this;
    }

    @Override
    public Dataset applyToClassifier(UnaryOperator<Classifier> operator) {
        Classifier result = operator.apply(assertClassifier());
        AssertState.notNull(result, "Null classifier");
        classifier = result;
        return this;
    }

    @Override
    public Dataset consumeClassifier(Consumer<Classifier> consumer) {
        consumer.accept(assertClassifier());
        return this;
    }
    
    @Override
    public Dataset buildClassifier(String classifierSpec) {
        Classifier result = DatasetUtils.buildClassifier(getInstances(), classifierSpec);
        AssertState.notNull(result, "Null classifier");
        classifier = result;
        return this;
    }
    
    @Override
    public Dataset loadClassifier(Supplier<Classifier> supplier) {
        Classifier result = supplier.get();
        AssertState.notNull(result, "Null classifier");
        classifier = result;
        return this;
    }
    
    @Override
    public Classifier getClassifier() {
        return classifier;
    }
    
    @Override
    public Dataset applyToEvaluation(Function<Dataset, Evaluation> function) {
        Evaluation result = function.apply(this);
        AssertState.notNull(result, "Null evaluation");
        evaluation = result;
        return this;
    }

    @Override
    public Dataset applyToEvaluation(UnaryOperator<Evaluation> operator) {
        Evaluation result = operator.apply(assertEvaluation());
        AssertState.notNull(result, "Null evaluation");
        evaluation = result;
        return this;
    }

    @Override
    public Dataset consumeEvaluation(Consumer<Evaluation> consumer) {
        consumer.accept(assertEvaluation());
        return this;
    }

    @Override
    public Dataset crossValidateModel(int numFolds, int seed) {
        Instances data = getInstances();
        try {
            Evaluation ev = evaluate().getEvaluation();
            ev.crossValidateModel(assertClassifier(), data, numFolds, new Random(seed));
        } catch (Exception ex) {
            throw UncheckedException.create(ex);
        }
        return this;
    }

    @Override
    public Dataset evaluateModel(Dataset dataset) {
        try {
            Evaluation ev = evaluate().getEvaluation();
            Instances testing = dataset.getInstances();
            ev.evaluateModel(assertClassifier(), testing);
        } catch (Exception ex) {
            throw UncheckedException.create(ex);
        }
        return this;
    }
    
    @Override
    public Dataset evaluateModel() {
        try {
            Evaluation ev = evaluate().getEvaluation();
            ev.evaluateModel(assertClassifier(), instances);
        } catch (Exception ex) {
            throw UncheckedException.create(ex);
        }
        return this;
    }
    
    @Override
    public Dataset evaluate() {
        try {
            evaluation = new Evaluation(instances);
        } catch (Exception ex) {
            throw UncheckedException.create(ex);
        }
        return this;
    }
    
    @Override
    public Evaluation getEvaluation() {
        return evaluation;
    }

    @Override
    public Dataset consumeDataset(Consumer<Dataset> consumer) {
        consumer.accept(this);
        return this;
    }
    

    private Classifier assertClassifier() {
        AssertState.notNull(classifier, "Classifier not available");
        return classifier;
    }
    
    private Evaluation assertEvaluation() {
        AssertState.notNull(evaluation, "Evaluation not available");
        return evaluation;
    }
}
