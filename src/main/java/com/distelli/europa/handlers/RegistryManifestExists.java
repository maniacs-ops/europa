package com.distelli.europa.handlers;

import com.distelli.europa.EuropaRequestContext;
import org.eclipse.jetty.http.HttpMethod;
import com.distelli.webserver.RequestHandler;
import com.distelli.webserver.WebResponse;
import com.distelli.webserver.RequestContext;
import com.fasterxml.jackson.databind.JsonNode;
import javax.inject.Singleton;
import java.util.Map;
import java.util.HashMap;
import javax.inject.Inject;
import lombok.extern.log4j.Log4j;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import com.distelli.europa.db.RegistryManifestDb;
import com.distelli.europa.models.ContainerRepo;
import com.distelli.europa.util.ObjectKeyFactory;
import com.distelli.objectStore.ObjectKey;
import com.distelli.objectStore.ObjectStore;
import com.distelli.objectStore.ObjectMetadata;
import com.distelli.europa.models.RegistryManifest;
import com.distelli.europa.registry.RegistryError;
import com.distelli.europa.registry.RegistryErrorCode;
import javax.inject.Provider;

@Log4j
@Singleton
public class RegistryManifestExists extends RegistryBase {
    @Inject
    private Provider<ObjectKeyFactory> _objectKeyFactoryProvider;
    @Inject
    private Provider<ObjectStore> _objectStoreProvider;
    @Inject
    private RegistryManifestDb _manifestDb;

    public WebResponse handleRegistryRequest(EuropaRequestContext requestContext) {
        String ownerDomain = requestContext.getOwnerDomain();
        String name = requestContext.getMatchedRoute().getParam("name");
        String reference = requestContext.getMatchedRoute().getParam("reference");

        ContainerRepo repo = getContainerRepo(ownerDomain, name);
        if ( null == repo ) {
            throw new RegistryError(
                "No manifest exists with name="+name+" reference="+reference,
                RegistryErrorCode.MANIFEST_UNKNOWN);
        }

        RegistryManifest manifest = _manifestDb.getManifestByRepoIdTag(ownerDomain, repo.getId(), reference);
        if ( null == manifest ) {
            throw new RegistryError(
                "No manifest exists with name="+name+" reference="+reference,
                RegistryErrorCode.MANIFEST_UNKNOWN);
        }

        ObjectKeyFactory objectKeyFactory = _objectKeyFactoryProvider.get();
        ObjectKey objKey = objectKeyFactory.forRegistryManifest(manifest.getManifestId());
        ObjectStore objectStore = _objectStoreProvider.get();
        ObjectMetadata objMeta = objectStore.head(objKey);
        if ( null == objMeta ) {
            throw new RegistryError(
                "Manifest is missing from object store. "+objKey,
                RegistryErrorCode.MANIFEST_UNKNOWN);
        }

        WebResponse response = new WebResponse(200);

        response.setContentType(manifest.getContentType());
        response.setResponseHeader("Content-Length", ""+objMeta.getContentLength());
        response.setResponseHeader("Docker-Content-Digest", manifest.getManifestId());
        return response;
    }
}
