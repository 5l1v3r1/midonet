/*
* Copyright 2012 Midokura Europe SARL
*/
package com.midokura.mmdpctl.commands.results;

public class AddDatapathResult implements Result {

    @Override
    public void printResult() {
        System.out.println("Datapath created successfully.");
    }
}
