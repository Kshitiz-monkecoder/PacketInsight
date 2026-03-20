package com.dpi.rules;

import com.dpi.types.AppType;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rule Manager - Manages blocking rules for IPs, applications, and domains
 * Thread-safe for use in multi-threaded pipeline
 */
public class RuleManager {
    private final Set<InetAddress> blockedIPs = ConcurrentHashMap.newKeySet();
    private final Set<AppType> blockedApps = ConcurrentHashMap.newKeySet();
    private final Set<String> blockedDomains = ConcurrentHashMap.newKeySet();

    /**
     * Add IP to block list
     */
    public void blockIP(InetAddress ip) {
        blockedIPs.add(ip);
        System.out.println("[Rules] Blocked IP: " + ip.getHostAddress());
    }

    /**
     * Add application to block list
     */
    public void blockApp(AppType app) {
        blockedApps.add(app);
        System.out.println("[Rules] Blocked app: " + app.getDisplayName());
    }

    /**
     * Add domain to block list (substring matching)
     */
    public void blockDomain(String domain) {
        blockedDomains.add(domain.toLowerCase());
        System.out.println("[Rules] Blocked domain: " + domain);
    }

    /**
     * Check if a packet should be blocked
     */
    public boolean isBlocked(InetAddress srcIP, AppType appType, String sni) {
        // Check IP blacklist
        if (blockedIPs.contains(srcIP)) {
            return true;
        }

        // Check app blacklist
        if (blockedApps.contains(appType)) {
            return true;
        }

        // Check domain blacklist (substring match)
        if (sni != null && !sni.isEmpty()) {
            String lowerSNI = sni.toLowerCase();
            for (String blockedDomain : blockedDomains) {
                if (lowerSNI.contains(blockedDomain)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Get count of active rules
     */
    public int getRuleCount() {
        return blockedIPs.size() + blockedApps.size() + blockedDomains.size();
    }

    /**
     * Print all active rules
     */
    public void printRules() {
        System.out.println("\n[Rules] Active blocking rules:");
        if (!blockedIPs.isEmpty()) {
            System.out.println("  Blocked IPs: " + blockedIPs.size());
            blockedIPs.forEach(ip -> System.out.println("    - " + ip.getHostAddress()));
        }
        if (!blockedApps.isEmpty()) {
            System.out.println("  Blocked Apps: " + blockedApps.size());
            blockedApps.forEach(app -> System.out.println("    - " + app.getDisplayName()));
        }
        if (!blockedDomains.isEmpty()) {
            System.out.println("  Blocked Domains: " + blockedDomains.size());
            blockedDomains.forEach(domain -> System.out.println("    - " + domain));
        }
        if (getRuleCount() == 0) {
            System.out.println("  No blocking rules configured");
        }
        System.out.println();
    }
}
