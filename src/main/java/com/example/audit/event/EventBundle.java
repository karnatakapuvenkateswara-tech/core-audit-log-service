package com.example.audit.event;

import java.util.List;

public record EventBundle(EventBundleManifest manifest, List<EventResponse> records) {
}
