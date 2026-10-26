package a.a.a;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import mod.hey.studios.project.ProjectSettings;
import mod.hilal.saif.components.ComponentExtraCode;
import mod.pranav.viewbinding.ViewBindingBuilder;

public class Hx {

    private final ProjectFileBean projectFileBean;
    private final jq jq;
    private final ArrayList<Event> viewEvents = new ArrayList<>();
    private final ArrayList<Event> componentEvents = new ArrayList<>();
    private final ArrayList<ActivityEvent> activityEvents = new ArrayList<>();
    private final ArrayList<Event> authEvents = new ArrayList<>();
    private final ArrayList<Event> drawerViewEvents = new ArrayList<>();
    private final ArrayList<ComponentCallback> callbackEvents = new ArrayList<>();
    private final ArrayList<String> imports = new ArrayList<>();
    private final HashMap<String, String> activityLifecycleEvents = new HashMap<>();
    private final Boolean isViewBindingEnabled;
    public String k = "";
    public String l = "";

    public Hx(jq logicHolder, ProjectFileBean projectFileBean, eC eC) {
        jq = logicHolder;
        this.projectFileBean = projectFileBean;

        ProjectSettings projectSettings = new ProjectSettings(logicHolder.sc_id);
        isViewBindingEnabled = projectSettings.getValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, "false").equals("true");

        ArrayList<ViewBean> views = new ArrayList<>(eC.d(projectFileBean.getXmlName()));
        if (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_FAB)) {
            ViewBean fab = eC.h(projectFileBean.getXmlName());
            views.add(fab);
        }
        for (ViewBean view : views) {
            viewEvents.add(new Event(this, isViewBindingEnabled ? "binding." + ViewBindingBuilder.generateParameterFromId(view.id) : view.id, view.getClassInfo(), isViewBindingEnabled));
        }

        ArrayList<ComponentBean> components = eC.e(projectFileBean.getJavaName());
        for (ComponentBean componentBean : components) {
            int type = componentBean.type;
            if (type == ComponentBean.COMPONENT_TYPE_FIREBASE_AUTH || type == ComponentBean.COMPONENT_TYPE_INTERSTITIAL_AD) {
                authEvents.add(new Event(this, componentBean.componentId, componentBean.getClassInfo(), isViewBindingEnabled));
            } else {
                componentEvents.add(new Event(this, componentBean.componentId, componentBean.getClassInfo(), isViewBindingEnabled));
            }
        }

        if (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
            ArrayList<ViewBean> drawerViews = eC.d(projectFileBean.getDrawerXmlName());
            for (ViewBean view : drawerViews) {
                drawerViewEvents.add(new Event(this, isViewBindingEnabled ? "binding.drawer." + ViewBindingBuilder.generateParameterFromId(view.id) : "_drawer_" + view.id, view.getClassInfo(), isViewBindingEnabled));
            }
        }

        a(eC.g(projectFileBean.getJavaName()), eC.b(projectFileBean.getJavaName()));
    }

    public String getOnActivityResultSwitchCases() {
        StringBuilder sb = new StringBuilder(4096);
        for (ComponentCallback value : callbackEvents) {
            String code = value.getCode();
            if (sb.length() > 0 && !code.isEmpty()) {
                sb.append(Jx.EOL);
                sb.append(Jx.EOL);
            }
            sb.append(code);
        }
        return sb.toString();
    }

    private void addCallbackEvents(int targetType, String targetId, String eventName, String eventLogic) {
        boolean hasOnSuccessLogic = eventName.equals("onPictureTaken") || eventName.equals("onAccountPicker") || eventName.equals("onFilesPicked");
        boolean hasOnCancelledLogic = eventName.equals("onPictureTakenCancel") || eventName.equals("onFilesPickedCancel") || eventName.equals("onAccountPickerCancelled");

        boolean alreadyRegistered = false;
        for (ComponentCallback next : callbackEvents) {
            if (next.componentName.equals(targetId)) {
                if (hasOnSuccessLogic) {
                    next.setOnSuccessLogic(eventLogic);
                    return;
                } else if (hasOnCancelledLogic) {
                    next.setOnCancelledLogic(eventLogic);
                    return;
                } else {
                    alreadyRegistered = true;
                }
            }
        }
        if (!alreadyRegistered) {
            ComponentCallback event = new ComponentCallback(targetType, targetId);
            if (hasOnSuccessLogic) {
                event.setOnSuccessLogic(eventLogic);
            } else if (hasOnCancelledLogic) {
                event.setOnCancelledLogic(eventLogic);
            }
            callbackEvents.add(event);
        }
    }

    private void addActivityEvent(String eventName, String eventLogic) {
        ActivityEvent target = null;

        for (ActivityEvent next : activityEvents) {
            if (next.name.equals(eventName)) {
                target = next;
                break;
            }
        }

        if (target == null) {
            target = new ActivityEvent(eventName);
            activityEvents.add(target);
        }
        target.setLogic(eventLogic);
    }

    public void addLifecycleEvent(String eventName, String viewType, String viewId) {
        if (!activityLifecycleEvents.containsKey(eventName)) {
            activityLifecycleEvents.put(eventName, Lx.getDefaultActivityLifecycleCode(eventName, viewType, isViewBindingEnabled ? "binding." + ViewBindingBuilder.generateParameterFromId(viewId) : viewId));
        }
    }

    private void a(ArrayList<EventBean> events, HashMap<String, ArrayList<BlockBean>> logicBlocks) {
        for (EventBean eventBean : events) {
            String eventKey = eventBean.targetId + "_" + eventBean.eventName;
            ArrayList<BlockBean> eventLogicBlocks = logicBlocks.get(eventKey);
            
            String eventLogic = (eventLogicBlocks == null || eventLogicBlocks.isEmpty()) ? "" :
                    new Fx(projectFileBean.getActivityName(), eventKey, jq, eventLogicBlocks, isViewBindingEnabled).a();

            switch (eventBean.eventType) {
                case EventBean.EVENT_TYPE_VIEW:
                    addViewListeners(eventBean.targetId, eventBean.eventName, eventLogic);
                    break;

                case EventBean.EVENT_TYPE_COMPONENT:
                    switch (eventBean.targetType) {
                        case ComponentBean.COMPONENT_TYPE_FIREBASE_AUTH:
                        case ComponentBean.COMPONENT_TYPE_INTERSTITIAL_AD:
                            addCallbackEvents(eventBean.targetId, eventBean.eventName, eventLogic);
                            break;

                        case ComponentBean.COMPONENT_TYPE_CAMERA:
                        case ComponentBean.COMPONENT_TYPE_FILE_PICKER:
                        case ComponentBean.COMPONENT_TYPE_FIREBASE_AUTH_GOOGLE_LOGIN:
                            addCallbackEvents(eventBean.targetType, eventBean.targetId, eventBean.eventName, eventLogic);
                            break;

                        default:
                            addViewImports(eventBean.targetId, eventBean.eventName, eventLogic);
                    }
                    break;

                case EventBean.EVENT_TYPE_ACTIVITY:
                    addActivityEvent(eventBean.eventName, eventLogic);
                    break;

                case EventBean.EVENT_TYPE_DRAWER_VIEW:
                    addDrawerEvents(eventBean.targetId, eventBean.eventName, eventLogic);
                    break;

                default:
            }
        }
    }

    public String generateActivityLifecycleEventCode() {
        StringBuilder sb = new StringBuilder(4096);
        for (Map.Entry<String, String> event : activityLifecycleEvents.entrySet()) {
            String name = event.getKey();
            String logic = event.getValue();

            boolean found = false;
            for (ActivityEvent next : activityEvents) {
                if (next.name.equals(name)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                ActivityEvent a = new ActivityEvent(name);
                a.setLogic(logic);
                activityEvents.add(a);
            }
        }

        for (ActivityEvent value : activityEvents) {
            String code = value.getCode();
            if (sb.length() > 0 && !code.isEmpty()) {
                sb.append(Jx.EOL);
                sb.append(Jx.EOL);
            }
            sb.append(code);
        }
        return sb.toString();
    }

    private void addViewImports(String targetId, String eventName, String eventLogic) {
        for (Event next : componentEvents) {
            if (next.id.equals(targetId)) {
                next.addEvent(targetId, eventName, eventLogic);
                return;
            }
        }
    }

    public String generateComponentEvents() {
        StringBuilder sb = new StringBuilder(4096);
        ComponentExtraCode componentExtraCode = new ComponentExtraCode(this, sb);
        for (Event next : componentEvents) {
            componentExtraCode.s(next.generateEvent());
        }
        return sb.toString();
    }

    public String generateGoogleBillingPurchaseUpdatedMethod() {
        ArrayList<Event> billingComponents = new ArrayList<>();
        for (Event componentEvent : componentEvents) {
            if (componentEvent.isGoogleBilling()) billingComponents.add(componentEvent);
        }
        if (billingComponents.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(8192);
        sb.append("@Override").append(Jx.EOL);
        sb.append("public void onPurchasesUpdated(BillingResult _billingResult, List<Purchase> _purchases) {").append(Jx.EOL);
        sb.append("final int _billingResponseCode = _billingResult == null ? -1 : _billingResult.getResponseCode();").append(Jx.EOL);
        sb.append("final String _billingDebugMessage = _sketchwareBillingSafeDebugMessage(_billingResult == null ? \"Unknown Billing result\" : _billingResult.getDebugMessage());").append(Jx.EOL);
        sb.append("final ArrayList<Purchase> _billingUpdatedPurchases = _purchases == null ? new ArrayList<>() : new ArrayList<>(_purchases);").append(Jx.EOL);

        for (Event componentEvent : billingComponents) {
            String id = componentEvent.id;
            sb.append("{").append(Jx.EOL);
            sb.append("final int _responseCode = _billingResponseCode;").append(Jx.EOL);
            sb.append("final String _debugMessage = _billingDebugMessage;").append(Jx.EOL);
            sb.append("final ArrayList<Purchase> _queriedPurchases = _billingUpdatedPurchases;").append(Jx.EOL);
            sb.append("_").append(id).append("_last_response_code = _responseCode;").append(Jx.EOL);
            sb.append("_").append(id).append("_last_debug_message = _debugMessage;").append(Jx.EOL);
            sb.append("if (_responseCode == BillingClient.BillingResponseCode.OK) {").append(Jx.EOL);
            sb.append("for (Purchase _billingIncomingPurchase : _queriedPurchases) {").append(Jx.EOL);
            sb.append("if (_billingIncomingPurchase == null) continue;").append(Jx.EOL);
            sb.append("boolean _billingPurchaseFound = false;").append(Jx.EOL);
            sb.append("String _billingIncomingToken = _billingIncomingPurchase.getPurchaseToken();").append(Jx.EOL);
            sb.append("for (int _billingIndex = 0; _billingIndex < _").append(id).append("_purchases.size(); _billingIndex++) {").append(Jx.EOL);
            sb.append("Purchase _billingExistingPurchase = _").append(id).append("_purchases.get(_billingIndex);").append(Jx.EOL);
            sb.append("if (_billingExistingPurchase != null && _billingIncomingToken != null && _billingIncomingToken.equals(_billingExistingPurchase.getPurchaseToken())) { _").append(id).append("_purchases.set(_billingIndex, _billingIncomingPurchase); _billingPurchaseFound = true; break; }").append(Jx.EOL);
            sb.append("}").append(Jx.EOL);
            sb.append("if (!_billingPurchaseFound) _").append(id).append("_purchases.add(_billingIncomingPurchase);").append(Jx.EOL);
            sb.append("if (_billingIncomingPurchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {").append(Jx.EOL);
            sb.append("if (_billingIncomingPurchase.isAcknowledged()) {").append(Jx.EOL);
            sb.append("if (_billingIncomingPurchase.getProducts() != null) for (String _billingOwnedId : _billingIncomingPurchase.getProducts()) if (!_").append(id).append("_owned_product_ids.contains(_billingOwnedId)) _").append(id).append("_owned_product_ids.add(_billingOwnedId);").append(Jx.EOL);
            sb.append("} else if (_billingIncomingToken != null && !_billingIncomingToken.trim().isEmpty() && !_").append(id).append("_acknowledging_tokens.contains(_billingIncomingToken) && ").append(id).append(" != null && ").append(id).append(".isReady()) {").append(Jx.EOL);
            sb.append("_").append(id).append("_acknowledging_tokens.add(_billingIncomingToken);").append(Jx.EOL);
            sb.append(id).append(".acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(_billingIncomingToken).build(), _billingAckResult -> {").append(Jx.EOL);
            sb.append("_").append(id).append("_acknowledging_tokens.remove(_billingIncomingToken);").append(Jx.EOL);
            sb.append("_").append(id).append("_last_response_code = _billingAckResult == null ? -1 : _billingAckResult.getResponseCode();").append(Jx.EOL);
            sb.append("_").append(id).append("_last_debug_message = _sketchwareBillingSafeDebugMessage(_billingAckResult == null ? \"Unknown Billing result\" : _billingAckResult.getDebugMessage());").append(Jx.EOL);
            sb.append("if (_billingAckResult != null && _billingAckResult.getResponseCode() == BillingClient.BillingResponseCode.OK && _billingIncomingPurchase.getProducts() != null) for (String _billingOwnedId : _billingIncomingPurchase.getProducts()) if (!_").append(id).append("_owned_product_ids.contains(_billingOwnedId)) _").append(id).append("_owned_product_ids.add(_billingOwnedId);").append(Jx.EOL);
            sb.append("});").append(Jx.EOL).append("}").append(Jx.EOL).append("}").append(Jx.EOL).append("}").append(Jx.EOL);
            sb.append("}").append(Jx.EOL);
            sb.append("final int _purchaseCount = _queriedPurchases.size();").append(Jx.EOL);
            sb.append("final Purchase _firstPurchase = _queriedPurchases.isEmpty() ? null : _queriedPurchases.get(0);").append(Jx.EOL);
            sb.append("final String _productId = _firstPurchase == null || _firstPurchase.getProducts() == null || _firstPurchase.getProducts().isEmpty() ? \"\" : _firstPurchase.getProducts().get(0);").append(Jx.EOL);
            sb.append("final int _purchaseState = _firstPurchase == null ? 0 : _firstPurchase.getPurchaseState();").append(Jx.EOL);
            sb.append("final boolean _isAcknowledged = _firstPurchase != null && _firstPurchase.isAcknowledged();").append(Jx.EOL);
            String eventLogic = componentEvent.getEventLogic("onPurchasesUpdated");
            if (eventLogic.isEmpty()) eventLogic = componentEvent.getEventLogic("onGoogleBillingPurchasesUpdated");
            if (!eventLogic.isEmpty()) sb.append(eventLogic).append(Jx.EOL);
            sb.append("}").append(Jx.EOL);
        }
        sb.append("}");
        return sb.toString();
    }

    private void addDrawerEvents(String targetId, String eventName, String eventLogic) {
        for (Event next : drawerViewEvents) {
            if (next.id.equals(isViewBindingEnabled ? "binding.drawer." + ViewBindingBuilder.generateParameterFromId(targetId) : "_drawer_" + targetId)) {
                next.addEvent(targetId, eventName, eventLogic);
                return;
            }
        }
    }

    public String generateDrawerEvents() {
        StringBuilder sb = new StringBuilder(4096);
        for (Event drawerViewEvent : drawerViewEvents) {
            String eventCodes = drawerViewEvent.generateEvent();
            if (sb.length() > 0 && !eventCodes.isEmpty()) {
                sb.append(Jx.EOL);
                sb.append(Jx.EOL);
            }
            sb.append(eventCodes);
        }
        return sb.toString();
    }

    private void addCallbackEvents(String targetId, String eventName, String eventLogic) {
        for (Event next : authEvents) {
            if (next.id.equals(targetId)) {
                next.addEvent(targetId, eventName, eventLogic);
                return;
            }
        }
    }

    /**
     * @return {@link Hx#imports}
     */
    public ArrayList<String> getImports() {
        return imports;
    }

    private void addViewListeners(String targetId, String eventName, String eventLogic) {
        for (Event next : viewEvents) {
            if (next.id.equals(isViewBindingEnabled ? "binding." + ViewBindingBuilder.generateParameterFromId(targetId) : targetId)) {
                next.addEvent(targetId, eventName, eventLogic);
                return;
            }
        }
    }

    public String generateAuthEvents() {
        StringBuilder sb = new StringBuilder(4096);
        for (Event authEvent : authEvents) {
            String event = authEvent.generateEvent();
            if (sb.length() > 0 && !event.isEmpty()) {
                sb.append(Jx.EOL);
                sb.append(Jx.EOL);
            }
            sb.append(event);
        }
        return sb.toString();
    }

    public String generateViewEvents() {
        StringBuilder sb = new StringBuilder(4096);
        for (Event value : viewEvents) {
            String event = value.generateEvent();
            if (sb.length() > 0 && !event.isEmpty()) {
                sb.append(Jx.EOL);
                sb.append(Jx.EOL);
            }
            sb.append(event);
        }
        return sb.toString();
    }

    private static class ActivityEvent {

        private final String name;
        private String logic = "";
        private String targetId = "";

        private ActivityEvent(String name) {
            this.name = name;
        }

        private String getCode() {
            return Lx.getEventCode(targetId, name, logic);
        }

        private void setLogic(String logic) {
            this.logic = logic;
        }

        private void setTargetId(String targetId) {
            this.targetId = targetId;
        }
    }

    private static class ComponentCallback {

        private final int componentId;
        private final String componentName;
        private String onSuccessCode = "";
        private String onCancelledCode = "";

        private ComponentCallback(int componentId, String componentName) {
            this.componentId = componentId;
            this.componentName = componentName;
        }

        private void setOnCancelledLogic(String onCancelledLogic) {
            onCancelledCode = onCancelledLogic;
        }

        private void setOnSuccessLogic(String onSuccessLogic) {
            onSuccessCode = onSuccessLogic;
        }

        private String getCode() {
            return Lx.getOnActivityResultCode(componentId, componentName, onSuccessCode, onCancelledCode);
        }
    }

    private static class Event {
        private final Hx hx;
        private final String id;
        private final Gx classInfo;
        private final ArrayList<ComponentEvents> listeners = new ArrayList<>();

        private Event(Hx hx, String id, Gx classInfo, boolean isViewBindingEnabled) {
            this.hx = hx;
            this.id = id;
            this.classInfo = classInfo;

            String[] listeners = oq.getListenersForClass(classInfo);
            if (listeners.length > 0) {
                for (String listener : listeners) {
                    /* Found functionally same instructions in vanilla Sketchware, keep it this way */
                    if (!this.listeners.contains(listener)) {
                        this.listeners.add(new ComponentEvents(listener));
                    }
                }

                switch (classInfo.getClassName()) {
                    case "FirebaseDB":
                    case "FirebaseStorage":
                    case "FirebaseAuth":
                    case "Gyroscope":
                    case "FragmentAdapter":
                    case "WebView":
                    case "MapView":
                    case "InterstitialAd":
                    case "UnityAds":
                    case "RequestNetwork":
                    case "BluetoothConnect":
                        for (ComponentEvents value : this.listeners) {
                            value.isInitialized = true;
                        }
                        break;

                    default:
                }
            }
        }

        private boolean isGoogleBilling() {
            return classInfo != null && classInfo.a("GoogleBilling");
        }

        private String getEventLogic(String eventName) {
            for (ComponentEvents listener : listeners) {
                if (!"googleBillingPurchaseListener".equals(listener.listenerName)) continue;
                for (ActivityEvent activityEvent : listener.activityEvents) {
                    if (activityEvent.name.equals(eventName)) return activityEvent.logic;
                }
            }
            return "";
        }

        private String generateEvent() {
            StringBuilder sb = new StringBuilder(4096);
            for (ComponentEvents value : listeners) {
                String event = value.generateEvent(id);
                if (sb.length() > 0 && !event.isEmpty()) {
                    sb.append(Jx.EOL);
                    sb.append(Jx.EOL);
                }
                sb.append(event);
            }

            return sb.toString();
        }

        private void addEvent(String targetId, String eventName, String eventLogic) {
            for (ComponentEvents listener : listeners) {
                for (ActivityEvent activityEvent : listener.activityEvents) {
                    if (activityEvent.name.equals(eventName)) {
                        activityEvent.setLogic(eventLogic);
                        activityEvent.setTargetId(targetId);
                        listener.isInitialized = true;
                    }
                }

                if (listener.isInitialized) {
                    hx.imports.addAll(mq.d(listener.listenerName));
                }
            }
        }

    }

    private static class ComponentEvents {

        private final String listenerName;
        private final ArrayList<ActivityEvent> activityEvents;
        /**
         * Probably "if associated to a Component"/"got its code added"
         */
        private boolean isInitialized = false;

        private ComponentEvents(String listenerName) {
            this.listenerName = listenerName;
            activityEvents = new ArrayList<>();
            for (String eventName : oq.getEventsForListener(listenerName)) {
                activityEvents.add(new ActivityEvent(eventName));
            }
        }

        private String generateEvent(String componentName) {
            if (!isInitialized) {
                return "";
            }
            StringBuilder eventsCode = new StringBuilder(4096);
            for (ActivityEvent activityEvent : activityEvents) {
                String code = activityEvent.getCode();
                if (eventsCode.length() > 0 && !code.isEmpty()) {
                    eventsCode.append(Jx.EOL);
                    eventsCode.append(Jx.EOL);
                }
                eventsCode.append(code);
            }
            return Lx.getListenerCode(listenerName, componentName, eventsCode.toString());
        }
    }
}
