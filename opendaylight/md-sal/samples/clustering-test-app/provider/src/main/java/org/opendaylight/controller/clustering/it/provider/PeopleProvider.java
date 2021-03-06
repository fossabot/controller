/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.clustering.it.provider;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.config.sal.clustering.it.car.purchase.rev140818.CarPurchaseService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.config.sal.clustering.it.people.rev140818.AddPersonInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.config.sal.clustering.it.people.rev140818.AddPersonOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.config.sal.clustering.it.people.rev140818.AddPersonOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.config.sal.clustering.it.people.rev140818.People;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.config.sal.clustering.it.people.rev140818.PeopleService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.config.sal.clustering.it.people.rev140818.PersonContext;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.config.sal.clustering.it.people.rev140818.people.Person;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.config.sal.clustering.it.people.rev140818.people.PersonBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeopleProvider implements PeopleService, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PeopleProvider.class);

    private DataBroker dataProvider;

    private BindingAwareBroker.RoutedRpcRegistration<CarPurchaseService> rpcRegistration;

    public void setDataProvider(final DataBroker salDataProvider) {
        this.dataProvider = salDataProvider;
    }


    public void setRpcRegistration(final BindingAwareBroker.RoutedRpcRegistration<CarPurchaseService> rpcRegistration) {
        this.rpcRegistration = rpcRegistration;
    }

    @Override
    public ListenableFuture<RpcResult<AddPersonOutput>> addPerson(final AddPersonInput input) {
        LOG.info("RPC addPerson : adding person [{}]", input);

        PersonBuilder builder = new PersonBuilder(input);
        final Person person = builder.build();
        final SettableFuture<RpcResult<AddPersonOutput>> futureResult = SettableFuture.create();

        // Each entry will be identifiable by a unique key, we have to create that identifier
        final InstanceIdentifier<Person> personId = InstanceIdentifier.builder(People.class)
                .child(Person.class, person.key()).build();
        // Place entry in data store tree
        WriteTransaction tx = dataProvider.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, personId, person, true);

        Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                LOG.info("RPC addPerson : person added successfully [{}]", person);
                rpcRegistration.registerPath(PersonContext.class, personId);
                LOG.info("RPC addPerson : routed rpc registered for instance ID [{}]", personId);
                futureResult.set(RpcResultBuilder.success(new AddPersonOutputBuilder().build()).build());
            }

            @Override
            public void onFailure(final Throwable ex) {
                LOG.error("RPC addPerson : person addition failed [{}]", person, ex);
                futureResult.set(RpcResultBuilder.<AddPersonOutput>failed()
                        .withError(RpcError.ErrorType.APPLICATION, ex.getMessage()).build());
            }
        }, MoreExecutors.directExecutor());
        return futureResult;
    }

    @Override
    public void close() {
    }
}
