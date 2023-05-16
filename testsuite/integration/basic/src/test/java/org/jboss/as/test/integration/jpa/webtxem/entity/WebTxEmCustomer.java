/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.integration.jpa.webtxem.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;

import java.util.Set;

/**
 * Customer entity class
 *
 * @author Zbyněk Roubalík
 */
@Entity
@NamedQueries(
        {@NamedQuery(name = "allCustomers", query = "select c from WebTxEmCustomer c"),
                @NamedQuery(name = "customerById", query = "select c from WebTxEmCustomer c where c.id=:id")})
public class WebTxEmCustomer {

    private Long id;
    private String name;
    private String address;
    private Set<WebTxEmTicket> tickets;
    private Set<WebTxEmFlight> flights;

    // Address address;

    public WebTxEmCustomer() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, mappedBy = "customer")
    public Set<WebTxEmTicket> getTickets() {
        return tickets;
    }

    public void setTickets(Set<WebTxEmTicket> tickets) {
        this.tickets = tickets;
    }

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.EAGER, mappedBy = "customers")
    public Set<WebTxEmFlight> getFlights() {
        return flights;
    }

    public void setFlights(Set<WebTxEmFlight> flights) {
        this.flights = flights;
    }


}
