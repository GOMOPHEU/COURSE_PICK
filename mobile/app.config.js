module.exports = ({ config }) => {
  const mapKey = process.env.GOOGLE_MAPS_API_KEY;
  const apiUrl = process.env.EXPO_PUBLIC_API_URL;
  const allowLocalHttp = process.env.EXPO_PUBLIC_USE_MOCK === 'false' && (!apiUrl || apiUrl.startsWith('http://'));

  return {
    ...config,
    name: '코스픽',
    android: { ...config.android, package: config.android?.package ?? 'com.coursepick.mobile' },
    extra: { ...config.extra, hasAndroidMapsKey: Boolean(mapKey) },
    plugins: [
      ...(config.plugins ?? []),
      ...(mapKey ? [['react-native-maps', { androidGoogleMapsApiKey: mapKey }]] : []),
      ...(allowLocalHttp ? [['expo-build-properties', { android: { usesCleartextTraffic: true } }]] : []),
    ],
  };
};
