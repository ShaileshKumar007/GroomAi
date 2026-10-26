export const Platform = {
  OS: 'ios',
  select: (obj: Record<string, unknown>) => obj.ios ?? obj.default,
};

export const StyleSheet = {
  create: (styles: Record<string, unknown>) => styles,
};

export default {
  Platform,
  StyleSheet,
};
