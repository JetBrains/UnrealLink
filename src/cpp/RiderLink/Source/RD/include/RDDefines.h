#pragma once

#if defined(_MSC_VER)
#define RD_PUSH_STL_EXPORTS_WARNINGS \
  __pragma(warning(push)) \
  __pragma(warning(disable:4251))
#define RD_POP_STL_EXPORTS_WARNINGS __pragma(warning(pop))
#else
#define RD_PUSH_STL_EXPORTS_WARNINGS
#define RD_POP_STL_EXPORTS_WARNINGS
#endif