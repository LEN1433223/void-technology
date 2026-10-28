package com.voidtechnology;

import net.neoforged.fml.ModList;

/**
 * AE2 soft-dependency gate.
 *
 * <p>This mod is an addon for Applied Energistics 2, but it has to load and run fine when AE2 is
 * absent. Rule for the whole code base: any reference to {@code appeng.api} types must sit behind
 * an {@link #isLoaded()} check, so the classes are only linked when AE2 is actually present
 * (the API jar is a {@code compileOnly} dependency and is never shipped in our jar).</p>
 */
public final class Ae2Compat {

    /** Modern AE2 mod id (the pre-1.20 id {@code appliedenergistics2} is gone). */
    public static final String AE2_MOD_ID = "ae2";

    private Ae2Compat() {
    }

    /** True when Applied Energistics 2 is loaded; safe to call at any point after mod discovery. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(AE2_MOD_ID);
    }

    /**
     * Called once during common setup. When AE2 is missing this logs and returns, leaving every
     * AE2-dependent feature switched off.
     */
    public static void init() {
        if (!isLoaded()) {
            VoidTechnology.LOGGER.info("Applied Energistics 2 ({}) is not installed - AE2 features disabled",
                    AE2_MOD_ID);
            return;
        }
        VoidTechnology.LOGGER.info("Applied Energistics 2 detected - AE2 features enabled");
    }
}
