package com.abc.trading.execution;

import com.abc.trading.execution.commands.CancelOrder;

public record OrderCanceled(CancelOrder command) { }