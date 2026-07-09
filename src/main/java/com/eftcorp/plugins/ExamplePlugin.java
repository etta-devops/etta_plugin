/*
 * Copyright (c) 2026 EFT CORPORATION LIMITED (UK) Company Number 12528125. All rights reserved.
 * Unauthorized use, reproduction, or distribution of this software, in whole or in part, is strictly prohibited.
 * Use of this code for AI training is strictly prohibited and may result in legal action.
 */
/*
 * Example Payment Plugin — descriptor class.
 *
 * Rules:
 *   1. Exactly ONE class per JAR may carry @PluginDescriptor.
 *   2. The 'name' field is the plugin's identity, used to unload/reload it.
 *      It does NOT need to match the payment type — that is the @ExtensionProvider
 *      name on the processor bean (see ExamplePaymentProcessor).
 */
package com.eftcorp.plugins;

import com.ukheshe.arch.plugin.PluginDescriptor;

@PluginDescriptor(name = "demo-payment-plugin", version = "1.0.1", description = "This is biruk testing wallet mock api integration")
public class ExamplePlugin {
    // Descriptor-only class — no logic here.
}
