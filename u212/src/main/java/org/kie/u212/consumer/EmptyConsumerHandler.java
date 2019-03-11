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
package org.kie.u212.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.kie.u212.infra.consumer.ConsumerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmptyConsumerHandler implements ConsumerHandler {

    private Logger logger = LoggerFactory.getLogger(EmptyConsumerHandler.class);

    @Override
    public void process(ConsumerRecord record) {
        logger.info("Process event:{}",record);
    }
}
