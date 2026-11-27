import { Image } from 'expo-image';
import { StyleSheet } from 'react-native';

export function SeoulIllustration() {
  return <Image source={require('../../assets/images/seoul-illustration.jpg')} style={styles.image} contentFit="contain" />;
}

const styles = StyleSheet.create({
  image: { width: '100%', aspectRatio: 2, borderRadius: 12 },
});
