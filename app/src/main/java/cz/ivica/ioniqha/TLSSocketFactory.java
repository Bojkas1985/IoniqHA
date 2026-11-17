package cz.ivica.ioniqha;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starý Android (API 17) na head-unitu má v SSLSocketFactory ve výchozím
 * stavu povolené jen TLSv1/TLSv1.0 - moderní servery (Nabu Casa, Let's
 * Encrypt koncovky apod.) ale vyžadují aspoň TLSv1.2 a handshake jinak
 * selže. Tahle třída je jen tenký obal kolem výchozí SSLSocketFactory,
 * co na každém vytvořeném socketu natvrdo zapne TLSv1.1/TLSv1.2.
 *
 * Doplněno 2026-11-17: samotné vynucení TLSv1.2 nestačilo - v logu pořád
 * skončilo "Connection closed by peer" hned po handshake. Nejpravděpodobnější
 * příčina je chybějící SNI (Server Name Indication) - starý
 * OpenSSLSocketImpl na API 17 ho při vytvoření socketu přes hostname/port
 * automaticky neposílá, a Nabu Casa (sdílená infrastruktura pro spoustu
 * zákaznických domén na stejné IP) bez SNI neví, jaký certifikát/vhost
 * poslat, a spojení zavře. Proto tu po vytvoření socketu zkoušíme reflexí
 * zavolat metodu "setHostname" (je na OpenSSLSocketImpl od API 17 přítomná,
 * i když není ve veřejném SSLSocket API), což SNI vynutí explicitně.
 *
 * Stejný problém a stejné řešení měl i původní ABRPTransmitter - tohle je
 * jen čistá reimplementace stejného principu, ne kopie jeho kódu.
 */
public class TLSSocketFactory extends SSLSocketFactory {

    private static final Logger LOG = LoggerFactory.getLogger(TLSSocketFactory.class);

    private static final String[] ENABLED_PROTOCOLS = {"TLSv1.1", "TLSv1.2"};

    private final SSLSocketFactory mDelegate;

    public TLSSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        // Na starém Androidu (API 17) nestačí vzít obecný "TLS" kontext a pak
        // jen natvrdo zapnout TLSv1.1/1.2 na socketu - obecný "TLS" kontext
        // tu bývá interně navázaný jen na starou TLSv1.0 implementaci a
        // handshake stejně selže ("Connection closed by peer"). Je potřeba
        // si vyžádat SSLContext rovnou pro "TLSv1.2" jménem algoritmu.
        SSLContext context;
        try {
            context = SSLContext.getInstance("TLSv1.2");
        } catch (NoSuchAlgorithmException e) {
            context = SSLContext.getInstance("TLS");
        }
        context.init(null, null, null);
        mDelegate = context.getSocketFactory();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return mDelegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return mDelegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return enableTls(mDelegate.createSocket(), null);
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return enableTls(mDelegate.createSocket(s, host, port, autoClose), host);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return enableTls(mDelegate.createSocket(host, port), host);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException {
        return enableTls(mDelegate.createSocket(host, port, localAddress, localPort), host);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return enableTls(mDelegate.createSocket(host, port), null);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return enableTls(mDelegate.createSocket(address, port, localAddress, localPort), null);
    }

    private Socket enableTls(Socket socket, String hostname) {
        if (socket instanceof SSLSocket) {
            SSLSocket sslSocket = (SSLSocket) socket;
            sslSocket.setEnabledProtocols(ENABLED_PROTOCOLS);
            if (hostname != null) {
                forceServerNameIndication(sslSocket, hostname);
            }
        }
        return socket;
    }

    /**
     * Reflexí zkusí na podkladovém OpenSSLSocketImpl vynutit odeslání SNI pro
     * daný hostname. Metoda "setHostname" není součástí veřejného SSLSocket
     * API, proto to jinak než reflexí nejde. Pokud na daném zařízení
     * neexistuje nebo volání selže, jen to zalogujeme a pokračujeme beze SNI
     * (lepší zkusit handshake bez SNI, než appku shodit).
     */
    private void forceServerNameIndication(SSLSocket socket, String hostname) {
        try {
            Method setHostname = socket.getClass().getMethod("setHostname", String.class);
            setHostname.invoke(socket, hostname);
        } catch (Exception e) {
            LOG.debug("Nepodařilo se vynutit SNI (setHostname) pro {}", hostname, e);
        }
    }
}
