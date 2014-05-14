package edu.yale.library.ladybird.web.view;

import edu.yale.library.ladybird.auth.PermissionsValue;
import edu.yale.library.ladybird.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import java.io.Serializable;
import java.util.List;

/**
 *
 */

@ManagedBean
@ViewScoped
public class PermsisionsView implements Serializable {

    private Logger logger = LoggerFactory.getLogger(PermsisionsView.class);

    private String role = "";
    private List<PermissionsValue> itemList;

    @PostConstruct
    public void init() {
        try {
            role = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("role");

            logger.debug("Getting permissiosn for role={}", role);

            itemList = getPermissionsValueForRole(role);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    //TODO
    private List<PermissionsValue> getPermissionsValueForRole(final String role) {
        if (role.equalsIgnoreCase("ADMIN")) {
            return Roles.ADMIN.getPermissions();
        } else if (role.equalsIgnoreCase("VISITOR")) {
            return Roles.VISITOR.getPermissions();
        }
        return Roles.NONE.getPermissions();
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<PermissionsValue> getItemList() {
        return itemList;
    }

    public void setItemList(List<PermissionsValue> itemList) {
        this.itemList = itemList;
    }

}

