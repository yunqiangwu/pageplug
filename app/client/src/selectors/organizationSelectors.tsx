import { createSelector } from "reselect";
import { AppState } from "reducers";
import { OrgRole, Org } from "constants/orgConstants";

export const getRolesFromState = (state: AppState) => state.ui.orgs.roles;
export const getOrgs = (state: AppState) => state.ui.orgs.list;
export const getAllUsers = (state: AppState) => state.ui.orgs.orgUsers;
export const getAllRoles = (state: AppState) => state.ui.orgs.orgRoles;

export const getCurrentUserOrgId = (state: AppState) =>
  state.ui.users.currentUser?.currentOrganizationId;

export const getCurrentOrg = createSelector(
  getOrgs,
  getCurrentUserOrgId,
  (orgs?: Org[], id?: string) => {
    if (id) {
      return orgs?.find(org => org.id === id);
    }
  },
);

export const getRoles = createSelector(getRolesFromState, (roles?: OrgRole[]):
  | OrgRole[]
  | undefined => {
  return roles?.map(role => ({
    id: role.id,
    name: role.displayName || role.name,
    isDefault: role.isDefault,
  }));
});

export const getDefaultRole = createSelector(getRoles, (roles?: OrgRole[]) => {
  return roles?.find(role => role.isDefault);
});
