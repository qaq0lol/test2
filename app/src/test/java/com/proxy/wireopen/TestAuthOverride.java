package com.proxy.wireopen;

import android.content.Context;
import org.junit.Test;
import static org.junit.Assert.*;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import java.io.StringReader;

public class TestAuthOverride {
    @Test
    public void testAuthType() throws Exception {
        String ovpn = "client\ndev tun\nremote 1.1.1.1 1194\n";
        ConfigParser parser = new ConfigParser();
        parser.parseConfig(new StringReader(ovpn));
        VpnProfile profile = parser.convertProfile();
        System.out.println("Default auth type: " + profile.mAuthenticationType);
        
        String ovpnCerts = "client\ndev tun\nremote 1.1.1.1 1194\n<cert>\nCERT\n</cert>\n<key>\nKEY\n</key>\n";
        parser = new ConfigParser();
        parser.parseConfig(new StringReader(ovpnCerts));
        VpnProfile profileCerts = parser.convertProfile();
        System.out.println("Certs auth type: " + profileCerts.mAuthenticationType);
        System.out.println("mClientCertFilename: " + profileCerts.mClientCertFilename);

        String ovpnUserPass = "client\ndev tun\nremote 1.1.1.1 1194\nauth-user-pass\n";
        parser = new ConfigParser();
        parser.parseConfig(new StringReader(ovpnUserPass));
        VpnProfile profileUserPass = parser.convertProfile();
        System.out.println("UserPass auth type: " + profileUserPass.mAuthenticationType);
    }
}
