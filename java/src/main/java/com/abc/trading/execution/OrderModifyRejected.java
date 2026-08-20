package com.abc.trading.execution;

import com.abc.trading.execution.commands.ModifyOrder;

public record OrderModifyRejected(ModifyOrder command, String reason) { }