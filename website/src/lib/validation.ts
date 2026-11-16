export const USERNAME_MAX = 12;
export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 64;

export function normalizeUsername(raw: string): string {
  return raw.trim().replace(/\s+/g, " ");
}

export function usernameProblem(raw: string): string | null {
  const name = normalizeUsername(raw);
  if (!name) return "Enter a username.";
  if (!/^[A-Za-z0-9 ]+$/.test(name)) return "Usernames can only use letters, numbers and spaces.";
  if (name.length > USERNAME_MAX) return `Usernames can be at most ${USERNAME_MAX} characters.`;
  return null;
}

export function passwordProblem(username: string, password: string): string | null {
  if (password.length < PASSWORD_MIN) return `Passwords must be at least ${PASSWORD_MIN} characters.`;
  if (password.length > PASSWORD_MAX) return `Passwords can be at most ${PASSWORD_MAX} characters.`;
  if (!password.trim()) return "Passwords can't be only spaces.";
  if (password.trim().toLowerCase() === normalizeUsername(username).toLowerCase()) {
    return "Your password can't be the same as your username.";
  }
  return null;
}
