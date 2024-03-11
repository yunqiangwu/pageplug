const pxtransform = require("./postcss.pxtransform");
const path = require("path");

const transformEditorSize = (rule) => {
  rule
    .oneOf("0")
    .use("2")
    .tap((options) => ({
      ...options,
      postcssOptions: {
        plugins: [
          // transform PagePlug component px(based on 450) to weapp unit rpx(based on 750)
          pxtransform(),
          ...options.postcssOptions.plugins,
        ],
      },
    }));
};

const config = {
  projectName: "PagePlug",
  date: "2021-11-8",
  designWidth: 750,
  deviceRatio: {
    640: 2.34 / 2,
    750: 1,
    828: 1.81 / 2,
  },
  sourceRoot: "src",
  outputRoot: "dist",
  alias: {
    "@appsmith": path.resolve(__dirname, "..", "src/ce"),
  },
  plugins: ["@tarojs/plugin-html", "tarojs-plugin-dart-sass"],
  defineConstants: {
    API_BASE_URL: '"https://lowcode.methodot.com/api/"',
    DEFAULT_APP: '"62381ecd6d28f663ab8ce90b"',
    EMPTY_IMAGE_URL: '"https://img.icons8.com/stickers/344/aquarium.png"',
  },
  copy: {
    patterns: [{ from: "src/worker/", to: "dist/worker/" }],
    options: {},
  },
  framework: "react",
  mini: {
    postcss: {
      pxtransform: {
        enable: true,
        config: {},
      },
      url: {
        enable: true,
        config: {
          limit: 1024, // 设定转换尺寸上限
        },
      },
      cssModules: {
        enable: false, // 默认为 false，如需使用 css modules 功能，则设为 true
        config: {
          namingPattern: "module", // 转换模式，取值为 global/module
          generateScopedName: "[name]__[local]___[hash:base64:5]",
        },
      },
    },
    webpackChain(chain, webpack) {
      // add postcss plugin
      transformEditorSize(chain.module.rule("less"));
      transformEditorSize(chain.module.rule("nomorlCss"));
      chain.merge({
        resolve: {
          modules: ["./src"],
        },
      });
      chain.module
        .rule("script")
        .use("linariaLoader")
        .loader("linaria/loader")
        .options({
          sourceMap: process.env.NODE_ENV !== "production",
        });
    },
  },
  h5: {
    devServer: {
      hot: false  // 这一行
    },
    publicPath: "/",
    staticDirectory: "static",
    postcss: {
      autoprefixer: {
        enable: true,
        config: {},
      },
      cssModules: {
        enable: false, // 默认为 false，如需使用 css modules 功能，则设为 true
        config: {
          namingPattern: "module", // 转换模式，取值为 global/module
          generateScopedName: "[name]__[local]___[hash:base64:5]",
        },
      },
    },
    webpackChain(chain, webpack) {
      chain.merge({
        resolve: {
          modules: ["./src"],
        },
      });
      chain.module
        .rule("script")
        .use("linariaLoader")
        .loader("linaria/loader")
        .options({
          sourceMap: process.env.NODE_ENV !== "production",
        });
    },
  },
};

module.exports = function (merge) {
  if (process.env.NODE_ENV === "development") {
    return merge({}, config, require("./dev"));
  }
  return merge({}, config, require("./prod"));
};
