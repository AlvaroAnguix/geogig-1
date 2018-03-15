package org.locationtech.geogig.remote.http.pack;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.propagate;
import static org.locationtech.geogig.remote.http.pack.StreamingPackIO.Event.OBJECT_STREAM_END;
import static org.locationtech.geogig.remote.http.pack.StreamingPackIO.Event.OBJECT_STREAM_START;
import static org.locationtech.geogig.remote.http.pack.StreamingPackIO.Event.REF_END;
import static org.locationtech.geogig.remote.http.pack.StreamingPackIO.Event.REF_START;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.remotes.pack.LocalPackBuilder;
import org.locationtech.geogig.remotes.pack.Pack;
import org.locationtech.geogig.remotes.pack.PackBuilder;
import org.locationtech.geogig.remotes.pack.PackProcessor;
import org.locationtech.geogig.remotes.pack.RefRequest;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.BulkOpListener;

import com.google.common.base.Throwables;

public class StreamingPackWriter extends LocalPackBuilder implements PackBuilder, PackProcessor {

    private DataOutputStream out;

    private StreamingPackIO packIO = new StreamingPackIO();

    public StreamingPackWriter(Repository localRepo, DataOutputStream out) {
        super(localRepo);
        checkNotNull(out);
        this.out = out;
    }

    public @Override void start(Set<RevTag> tags) {
        super.start(tags);
        try {
            packIO.writeHeader(out);
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    public @Override void startRefResponse(RefRequest req) {
        super.startRefResponse(req);
        try {
            out.writeByte(REF_START.ordinal());
            new RefRequestIO().write(out, req);
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    public @Override void addCommit(RevCommit commit) {
        super.addCommit(commit);
        try {
            packIO.writeId(commit.getId(), out);
        } catch (IOException e) {
            propagate(e);
        }
    }

    public @Override void endRefResponse() {
        super.endRefResponse();
        try {
            packIO.writeId(ObjectId.NULL, out);
            out.writeByte(REF_END.ordinal());
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    public @Override Pack build() {
        return super.build();
    }

    public @Override void putAll(Iterator<? extends RevObject> iterator, BulkOpListener listener) {
        try {
            out.writeByte(OBJECT_STREAM_START.ordinal());
            while (iterator.hasNext()) {
                RevObject next = iterator.next();
                packIO.writeObject(next, out);
            }
            out.writeByte(OBJECT_STREAM_END.ordinal());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
