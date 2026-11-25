import { Image, StyleSheet } from 'react-native';

export function SeoulIllustration() {
  return <Image source={require('../../assets/images/seoul-illustration.jpg')} style={styles.image} resizeMode="cover" />;
}

const styles = StyleSheet.create({
  image: { width: '100%', aspectRatio: 1.9, borderRadius: 12 },
});
