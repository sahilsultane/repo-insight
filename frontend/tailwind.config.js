/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        navy: {
          50:  '#f0f4ff',
          100: '#e0e9ff',
          200: '#c1d3ff',
          300: '#92aeff',
          400: '#5b7fff',
          500: '#2d55fb',
          600: '#1a3ef2',
          700: '#142bdf',
          800: '#1224b5',
          900: '#14208e',
          950: '#0e1554',
        }
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif']
      }
    }
  },
  plugins: []
}
