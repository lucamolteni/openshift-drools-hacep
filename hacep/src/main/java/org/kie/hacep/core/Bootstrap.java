/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
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
 */
package org.kie.hacep.core;

import java.util.Arrays;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.hacep.Config;
import org.kie.hacep.EnvConfig;
import org.kie.hacep.consumer.DroolsConsumerHandler;
import org.kie.hacep.core.infra.SessionSnapShooter;
import org.kie.hacep.core.infra.SnapshotInfos;
import org.kie.hacep.core.infra.consumer.ConsumerController;
import org.kie.hacep.core.infra.consumer.Restarter;
import org.kie.hacep.core.infra.election.LeaderElection;
import org.kie.hacep.core.infra.election.State;
import org.kie.hacep.core.infra.producer.EventProducer;
import org.kie.hacep.core.infra.utils.Printer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Here is where we start all the services needed by the POD
 */
public class Bootstrap {

    private static final Logger logger = LoggerFactory.getLogger(Bootstrap.class);
    private static ConsumerController consumerController;
    private static EventProducer<?> eventProducer;
    private static Restarter restarter;
    private static SessionSnapShooter snapshooter;
    private static CoreKube coreKube;
    private static KieSessionHolder kieSessionHolder;

    public static void startEngine(Printer printer, EnvConfig envConfig) {
        //order matter
        kieSessionHolder = new KieSessionHolder();
        coreKube = new CoreKube(envConfig.getNamespace());
        leaderElection();
        startProducer(envConfig);
        startConsumers(printer, envConfig);
        addMasterElectionCallbacks();
        logger.info("CONFIGURE on start engine:{}", Config.getDefaultConfig());
    }

    public static void startEngine(Printer printer, EnvConfig envConfig, State initialState) {
        //order matter
        kieSessionHolder = new KieSessionHolder();
        coreKube = new CoreKube(envConfig.getNamespace(), initialState);
        leaderElection();
        startProducer(envConfig);
        startConsumers(printer, envConfig);
        addMasterElectionCallbacks();
        logger.info("CONFIGURE on start engine:{}", Config.getDefaultConfig());
    }

    public static void stopEngine() {
        logger.info("Stop engine");

        LeaderElection leadership = coreKube.getLeaderElection();
        try {
            leadership.stop();
        } catch (Exception e) {
            logger.error(e.getMessage(),
                         e);
        }
       kieSessionHolder.dispose();
        if (restarter != null) {
            restarter.getConsumer().stop();
        }
        if (eventProducer != null) {
            eventProducer.stop();
        }
        if(snapshooter != null) {
            snapshooter.close();
        }
        if(consumerController != null) {
            consumerController.stopConsumeEvents();
        }
        consumerController = null;
        eventProducer = null;
        restarter = null;
        snapshooter = null;
    }

    public static Restarter getRestarter(){
        return restarter;
    }


    private static void leaderElection() {
        //KubernetesLockConfiguration configuration = CoreKube.getKubernetesLockConfiguration();
        //@TODO configure from env the namespace
        //KubernetesClient client = Core.getKubeClient();
        //client.events().inNamespace("my-kafka-project").watch(WatcherFactory.createModifiedLogWatcher(configuration.getPodName()));
        LeaderElection leadership = coreKube.getLeaderElection();
        try {
            leadership.start();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private static void startProducer(EnvConfig envConfig) {
        eventProducer = new EventProducer<>();
        eventProducer.start(Config.getProducerConfig("EventProducer"));
    }

    private static void startConsumers(Printer printer,
                                       EnvConfig envConfig) {
        snapshooter = new SessionSnapShooter(envConfig);
        restarter = new Restarter(printer);
        restarter.createDroolsConsumer(envConfig);

        SnapshotInfos infos = snapshooter.deserialize();
        if (infos != null) {
            logger.info("start consumer with:{}", infos);
            initSessionHolder( infos, kieSessionHolder );
        } else {
            createClasspathSession( kieSessionHolder );
        }

        DroolsConsumerHandler handler = new DroolsConsumerHandler(eventProducer, snapshooter, envConfig, kieSessionHolder);
        restarter.getConsumer().createConsumer(handler, infos);

        consumerController = new ConsumerController(restarter);
        consumerController.consumeEvents();
    }

    private static void createClasspathSession( KieSessionHolder kieSessionHolder ) {
        KieServices srv = KieServices.get();
        if (srv != null) {
            KieContainer kieContainer = KieServices.get().newKieClasspathContainer();
            logger.info("Creating new Kie Session");
            kieSessionHolder.init(kieContainer.newKieSession());
        } else {
            logger.error("KieService is null");
        }
    }

    private static void initSessionHolder(SnapshotInfos infos, KieSessionHolder kieSessionHolder) {
        if (infos.getKieSession() == null) {
            KieContainer kieContainer = KieServices.get().newKieClasspathContainer();
            kieSessionHolder.init(kieContainer.newKieSession());
        } else {
            logger.info("Applying snapshot");
            kieSessionHolder.initFromSnapshot(infos);
        }
    }

    private static void addMasterElectionCallbacks() {
        coreKube.getLeaderElection().addCallbacks(Arrays.asList(restarter.getCallback(), eventProducer));
    }

}
