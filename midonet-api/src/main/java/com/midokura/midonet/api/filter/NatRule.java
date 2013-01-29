/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.api.filter;

import com.midokura.midolman.rules.RuleResult;

/**
 * NAT rule DTO
 */
public abstract class NatRule extends Rule {

    protected String flowAction;

    public NatRule() {
        super();
    }

    public NatRule(com.midokura.midonet.cluster.data.rules.NatRule rule) {
        super(rule);
        setFlowActionFromAction(rule.getAction());
    }

    /**
     * @return the flowAction
     */
    public String getFlowAction() {
        return flowAction;
    }

    /**
     * @param a
     *            the flowAction to set
     */
    public void setFlowActionFromAction(RuleResult.Action a) {
        switch (a) {
            case ACCEPT:
                flowAction = RuleType.Accept;
                break;
            case CONTINUE:
                flowAction = RuleType.Continue;
                break;
            case RETURN:
                flowAction = RuleType.Return;
                break;
            default:
                throw new IllegalArgumentException("Invalid action passed in.");
        }
    }

    public RuleResult.Action getNatFlowAction() {
        // ACCEPT, CONTINUE, RETURN
        if (flowAction.equals(RuleType.Accept)) {
            return RuleResult.Action.ACCEPT;
        } else if (flowAction.equals(RuleType.Continue)) {
            return RuleResult.Action.CONTINUE;
        } else if (flowAction.equals(RuleType.Return)) {
            return RuleResult.Action.RETURN;
        } else {
            throw new IllegalArgumentException("Invalid action passed in.");
        }

    }
}
